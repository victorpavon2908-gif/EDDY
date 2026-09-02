package com.niko.assistant.memory

import java.text.Normalizer
import java.util.Locale
import kotlin.math.ln

/**
 * Memoria de largo plazo completamente local.
 *
 * Inspirada en dos ideas útiles de Mem0/Letta, adaptadas al teléfono:
 *  - no enviar todo el historial al LLM; recuperar sólo recuerdos relevantes;
 *  - separar memoria estable (CORE), experiencias (EPISODIC) y hábitos (PROCEDURAL).
 *
 * No usa un servicio externo ni embeddings en la nube. El ranking combina palabras,
 * trigramas, recencia, confianza y uso. Los recuerdos episódicos expiran; los datos
 * CORE y procedimientos permanecen hasta que el usuario borra la memoria.
 */
class NikoLongTermMemory(
    private val archive: NikoMemoryArchive,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    enum class Kind { CORE, EPISODIC, PROCEDURAL }

    data class Hit(
        val key: String,
        val kind: Kind,
        val text: String,
        val score: Double,
    )

    fun observeUserTurn(text: String) {
        val clean = text.trim().take(MAX_EPISODE_CHARS)
        if (clean.length < 3 || containsSecret(clean)) return

        val facts = MemoryLearning.facts(clean)
        facts.forEach { (key, value) ->
            val normalizedValue = normalize(value)
            if (normalizedValue.isBlank()) return@forEach
            archive.upsertSemanticMemory(
                key = "core:$key",
                kind = Kind.CORE.name,
                text = "${factLabel(key)} $value".take(MAX_MEMORY_CHARS),
                normalized = normalize("${factLabel(key)} $value"),
                confidence = 1.0f,
                expiresAt = 0L,
            )
        }

        if (facts.isNotEmpty() || isWorthRemembering(clean)) {
            val normalized = normalize(clean)
            if (normalized.length >= 8) {
                archive.upsertSemanticMemory(
                    key = "episode:${Integer.toHexString(normalized.hashCode())}",
                    kind = Kind.EPISODIC.name,
                    text = clean,
                    normalized = normalized,
                    confidence = if (facts.isNotEmpty()) 0.92f else 0.72f,
                    expiresAt = nowMillis() + EPISODE_TTL_MS,
                )
            }
        }
    }

    fun rememberExplicitNote(text: String) {
        val clean = text.trim().take(MAX_MEMORY_CHARS)
        if (clean.isBlank() || containsSecret(clean)) return
        val normalized = normalize(clean)
        archive.upsertSemanticMemory(
            key = "core:note:${Integer.toHexString(normalized.hashCode())}",
            kind = Kind.CORE.name,
            text = clean,
            normalized = normalized,
            confidence = 1.0f,
            expiresAt = 0L,
        )
    }

    fun rememberProcedure(key: String, description: String) {
        val safeKey = normalize(key).replace(' ', '_').take(80)
        val clean = description.trim().take(MAX_MEMORY_CHARS)
        if (safeKey.isBlank() || clean.isBlank()) return
        archive.upsertSemanticMemory(
            key = "procedure:$safeKey",
            kind = Kind.PROCEDURAL.name,
            text = clean,
            normalized = normalize(clean),
            confidence = 0.86f,
            expiresAt = 0L,
        )
    }

    fun context(query: String, limit: Int = 7): String {
        val hits = retrieve(query, limit)
        if (hits.isEmpty()) return ""
        return hits.joinToString("\n") { hit -> "[${hit.kind.name}] ${hit.text}" }
    }

    fun retrieve(query: String, limit: Int = 7): List<Hit> {
        val now = nowMillis()
        val normalizedQuery = normalize(query)
        val queryTokens = tokens(normalizedQuery)
        val queryTrigrams = trigrams(normalizedQuery)
        val candidates = archive.semanticCandidates(MAX_CANDIDATES, now)
        if (candidates.isEmpty()) return emptyList()

        val ranked = candidates.mapNotNull { item ->
            val kind = runCatching { Kind.valueOf(item.kind) }.getOrDefault(Kind.EPISODIC)
            val score = if (normalizedQuery.isBlank()) {
                when (kind) {
                    Kind.CORE -> 0.82
                    Kind.PROCEDURAL -> 0.48
                    Kind.EPISODIC -> recency(item.updatedAt, now) * 0.42
                }
            } else {
                val memoryTokens = tokens(item.normalized)
                val tokenScore = jaccard(queryTokens, memoryTokens)
                val trigramScore = jaccard(queryTrigrams, trigrams(item.normalized))
                val exactBoost = when {
                    item.normalized == normalizedQuery -> 0.35
                    item.normalized.contains(normalizedQuery) || normalizedQuery.contains(item.normalized) -> 0.16
                    else -> 0.0
                }
                val kindBoost = when (kind) {
                    Kind.CORE -> 0.10
                    Kind.PROCEDURAL -> 0.06
                    Kind.EPISODIC -> 0.0
                }
                val accessBoost = (ln(1.0 + item.accessCount.toDouble()) / 12.0).coerceAtMost(0.08)
                0.50 * tokenScore +
                    0.22 * trigramScore +
                    0.08 * recency(item.updatedAt, now) +
                    0.08 * item.confidence.coerceIn(0f, 1f) +
                    exactBoost + kindBoost + accessBoost
            }
            val threshold = if (kind == Kind.CORE) 0.16 else 0.22
            if (score < threshold) null else Hit(item.key, kind, item.text, score)
        }.sortedByDescending(Hit::score).take(limit.coerceIn(1, 12))

        ranked.forEach { archive.touchSemanticMemory(it.key, now) }
        return ranked
    }

    fun count(): Long = archive.semanticMemoryCount(nowMillis())

    private fun isWorthRemembering(text: String): Boolean {
        if (text.length !in 12..MAX_EPISODE_CHARS) return false
        val value = normalize(text)
        return IMPORTANT_MARKERS.any(value::contains)
    }

    private fun containsSecret(text: String): Boolean {
        val value = normalize(text)
        return SECRET_MARKERS.any(value::contains)
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("(?i)\\b(?:niko|nico|nikko|nin)\\b"), " ")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokens(value: String): Set<String> = value.split(' ')
        .asSequence()
        .filter { it.length > 1 && it !in STOP_WORDS }
        .toSet()

    private fun trigrams(value: String): Set<String> {
        val compact = value.replace(" ", "")
        if (compact.length < 3) return if (compact.isBlank()) emptySet() else setOf(compact)
        return buildSet { for (index in 0..compact.length - 3) add(compact.substring(index, index + 3)) }
    }

    private fun <T> jaccard(a: Set<T>, b: Set<T>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size.toDouble().coerceAtLeast(1.0)
    }

    private fun recency(timestamp: Long, now: Long): Double {
        if (timestamp <= 0L || timestamp >= now) return 1.0
        val ageDays = (now - timestamp).toDouble() / DAY_MS
        return (1.0 / (1.0 + ageDays / 14.0)).coerceIn(0.0, 1.0)
    }

    private fun factLabel(key: String): String = when (key) {
        "name" -> "Tu nombre es"
        "likes" -> "Te gusta"
        "dislikes" -> "No te gusta"
        "prefers" -> "Preferís"
        "lives" -> "Vivís en"
        "work" -> "Trabajás en/como"
        "studies" -> "Estudiás"
        else -> key
    }

    companion object {
        private const val MAX_CANDIDATES = 180
        private const val MAX_MEMORY_CHARS = 700
        private const val MAX_EPISODE_CHARS = 900
        private const val DAY_MS = 86_400_000.0
        private const val EPISODE_TTL_MS = 45L * 24L * 60L * 60L * 1_000L

        private val IMPORTANT_MARKERS = setOf(
            "me gusta", "no me gusta", "prefiero", "quiero", "necesito", "tengo que",
            "acordate", "recorda", "recuerda", "mi proyecto", "estoy trabajando", "trabajo en",
            "estudio", "vivo en", "mañana", "la proxima", "la próxima", "siempre hago",
        )

        private val SECRET_MARKERS = setOf(
            "contraseña", "contrasena", "password", "cvv", "pin de", "codigo de verificacion",
            "código de verificación", "api key", "api_key", "secret key", "seed phrase",
            "frase semilla", "clave privada", "private key",
        )

        private val STOP_WORDS = setOf(
            "de", "la", "el", "los", "las", "un", "una", "que", "y", "o", "a", "en", "por",
            "para", "con", "mi", "me", "te", "se", "es", "del", "al", "lo", "como", "esto",
            "esa", "ese", "porfa", "favor", "haceme", "hazme",
        )
    }
}
