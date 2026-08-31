package com.eddy.assistant.ai

import android.content.Context
import com.eddy.assistant.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class EddyWebSource(
    val title: String,
    val url: String,
)

data class EddyAiReply(
    val text: String,
    val webUsed: Boolean,
    val sources: List<EddyWebSource>,
    val evidence: String = "",
)

/**
 * Cliente del backend de EDDY con memoria de conocimiento local ligera.
 *
 * Antes de consumir API busca una respuesta ya aprendida en el teléfono. Si encuentra
 * una consulta igual o muy parecida, responde desde memoria. Solo lo que todavía no sabe
 * llega al backend. Las respuestas nuevas se guardan localmente para reutilizarlas.
 */
class EddyAiClient(
    private val context: Context,
    private val baseUrlOverride: String? = null,
) {
    private val knowledgePrefs by lazy {
        context.applicationContext.getSharedPreferences(KNOWLEDGE_PREFS, Context.MODE_PRIVATE)
    }

    private fun resolvedBaseUrl(): String = baseUrlOverride
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
        ?: EddyAiSettings.baseUrl(context)

    val isConfigured: Boolean
        get() = resolvedBaseUrl().isNotBlank()

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = resolvedBaseUrl()
        if (baseUrl.isBlank()) return@withContext false
        val connection = runCatching {
            URL("${baseUrl.trimEnd('/')}/health").openConnection() as HttpURLConnection
        }.getOrNull() ?: return@withContext false
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "EDDY-Android/${BuildConfig.VERSION_NAME}")
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    suspend fun reply(
        message: String,
        memoryContext: String,
        forceWeb: Boolean = false,
    ): EddyAiReply? = withContext(Dispatchers.IO) {
        val cleanedMessage = message.trim()
        if (cleanedMessage.isBlank()) return@withContext null

        // Las búsquedas web forzadas deben ser frescas. Para conversación normal,
        // intentamos primero la memoria aprendida y evitamos consumir API innecesariamente.
        if (!forceWeb) {
            findLearnedReply(cleanedMessage)?.let { return@withContext it }
        }

        val baseUrl = resolvedBaseUrl()
        if (baseUrl.isBlank()) return@withContext null

        val endpoint = "${baseUrl.trimEnd('/')}/${if (forceWeb) "search" else "chat"}"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "EDDY-Android/${BuildConfig.VERSION_NAME}")
        }

        try {
            val payload = JSONObject()
                .put("message", cleanedMessage)
                .put("force_web", forceWeb)
                .put("memory_context", memoryContext.take(12_000))
                .put("memory_mode", "local-first")
                .toString()

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(payload) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) return@withContext null

            val json = JSONObject(body)
            val text = json.optString("reply").trim()
            if (text.isBlank()) return@withContext null

            val sourcesJson = json.optJSONArray("sources")
            val sources = buildList {
                if (sourcesJson != null) {
                    for (index in 0 until sourcesJson.length()) {
                        val item = sourcesJson.optJSONObject(index) ?: continue
                        val url = item.optString("url").trim()
                        if (url.isBlank()) continue
                        add(EddyWebSource(item.optString("title").trim().ifBlank { "Fuente web" }, url))
                    }
                }
            }

            val evidenceArray = json.optJSONArray("evidence")
            val evidence = buildString {
                if (evidenceArray != null) {
                    for (index in 0 until evidenceArray.length()) {
                        val item = evidenceArray.optJSONObject(index) ?: continue
                        val title = item.optString("title").trim()
                        val snippet = item.optString("snippet").trim()
                        val url = item.optString("url").trim()
                        if (snippet.isNotBlank()) appendLine("- $title: $snippet ($url)")
                    }
                }
            }.trim()

            val reply = EddyAiReply(
                text = text,
                webUsed = json.optBoolean("web_used", sources.isNotEmpty()),
                sources = sources,
                evidence = evidence,
            )

            rememberLearnedReply(cleanedMessage, reply)
            reply
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun findLearnedReply(message: String): EddyAiReply? {
        val target = normalize(message)
        if (target.length < MIN_CACHE_QUERY_LENGTH) return null
        val targetTokens = tokens(target)
        if (targetTokens.isEmpty()) return null

        val now = System.currentTimeMillis()
        val entries = readKnowledge()
        var best: KnowledgeEntry? = null
        var bestScore = 0.0

        for (entry in entries) {
            val maxAge = if (entry.webUsed) WEB_CACHE_TTL_MS else KNOWLEDGE_TTL_MS
            if (now - entry.savedAt > maxAge) continue
            val candidate = normalize(entry.question)
            if (candidate == target) {
                best = entry
                bestScore = 1.0
                break
            }
            val score = similarity(targetTokens, tokens(candidate))
            if (score > bestScore) {
                bestScore = score
                best = entry
            }
        }

        val hit = best?.takeIf { bestScore >= SIMILARITY_THRESHOLD } ?: return null
        return EddyAiReply(
            text = hit.answer,
            webUsed = false,
            sources = emptyList(),
            evidence = "",
        )
    }

    private fun rememberLearnedReply(question: String, reply: EddyAiReply) {
        if (question.length < MIN_CACHE_QUERY_LENGTH || reply.text.length < 2) return
        val normalizedQuestion = normalize(question)
        val entries = readKnowledge().toMutableList()

        entries.removeAll { normalize(it.question) == normalizedQuestion }
        entries.add(
            KnowledgeEntry(
                question = question.take(MAX_QUESTION_CHARS),
                answer = reply.text.take(MAX_ANSWER_CHARS),
                savedAt = System.currentTimeMillis(),
                webUsed = reply.webUsed,
            ),
        )

        while (entries.size > MAX_KNOWLEDGE_ENTRIES) entries.removeAt(0)

        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("q", entry.question)
                    .put("a", entry.answer)
                    .put("t", entry.savedAt)
                    .put("w", entry.webUsed),
            )
        }
        knowledgePrefs.edit().putString(KEY_KNOWLEDGE, array.toString()).apply()
    }

    private fun readKnowledge(): List<KnowledgeEntry> {
        val raw = knowledgePrefs.getString(KEY_KNOWLEDGE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val question = item.optString("q").trim()
                    val answer = item.optString("a").trim()
                    if (question.isBlank() || answer.isBlank()) continue
                    add(
                        KnowledgeEntry(
                            question = question,
                            answer = answer,
                            savedAt = item.optLong("t", 0L),
                            webUsed = item.optBoolean("w", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokens(value: String): Set<String> = value
        .split(' ')
        .asSequence()
        .map(String::trim)
        .filter { it.length >= 3 && it !in STOP_WORDS }
        .toSet()

    private fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }

    private data class KnowledgeEntry(
        val question: String,
        val answer: String,
        val savedAt: Long,
        val webUsed: Boolean,
    )

    companion object {
        private const val KNOWLEDGE_PREFS = "eddy_learned_knowledge_v1"
        private const val KEY_KNOWLEDGE = "entries"
        private const val MAX_KNOWLEDGE_ENTRIES = 120
        private const val MAX_QUESTION_CHARS = 500
        private const val MAX_ANSWER_CHARS = 6_000
        private const val MIN_CACHE_QUERY_LENGTH = 5
        private const val SIMILARITY_THRESHOLD = 0.88
        private const val KNOWLEDGE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        private const val WEB_CACHE_TTL_MS = 6L * 60L * 60L * 1_000L

        private val STOP_WORDS = setOf(
            "que", "como", "para", "por", "con", "una", "uno", "unos", "unas",
            "del", "las", "los", "esto", "esta", "este", "esa", "ese", "me", "mi",
            "es", "son", "hay", "quiero", "puedes", "puede", "favor",
        )
    }
}
