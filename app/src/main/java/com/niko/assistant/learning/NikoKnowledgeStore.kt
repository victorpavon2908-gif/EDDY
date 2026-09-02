package com.niko.assistant.learning

import android.content.Context
import com.niko.assistant.ai.AutonomousResearch
import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.brain.WebQueryRouter
import com.niko.assistant.memory.MemoryLearning
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Memoria documental local de NIKO.
 *
 * No modifica los pesos del LLM generativo. Conserva respuestas investigadas con sus
 * fuentes, caducidad y una clave semántica liviana para poder reutilizarlas sin Internet.
 * El clasificador OnlineIntentNetwork sigue siendo el componente que sí aprende pesos
 * localmente a partir de ejemplos confirmados.
 */
class NikoKnowledgeStore(context: Context) {
    data class Hit(
        val answer: String,
        val verified: Boolean,
        val sourceCount: Int,
        val learnedAt: Long,
    )

    private data class Entry(
        val key: String,
        val answer: String,
        val sources: List<String>,
        val verified: Boolean,
        val volatile: Boolean,
        val learnedAt: Long,
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Synchronized
    fun learn(query: String, reply: NikoAiReply) {
        val key = normalize(query)
        val answer = reply.text.trim()
        val urls = reply.sources.map { it.url.trim() }
            .filter { it.startsWith("https://") }
            .distinct()
            .take(MAX_SOURCES)
        if (!reply.webUsed || key.length < 3 || answer.isBlank() || urls.isEmpty()) return

        val verified = AutonomousResearch.publisherCount(urls) >= 2
        val entries = readEntries().toMutableList()
        entries.removeAll { it.key == key }
        entries += Entry(
            key = key,
            answer = answer.take(MAX_ANSWER_CHARS),
            sources = urls,
            verified = verified,
            volatile = WebQueryRouter.needsCurrentInformation(query),
            learnedAt = System.currentTimeMillis(),
        )
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
        saveEntries(entries)
    }

    /**
     * Returns only knowledge that is still fresh enough for its topic and sufficiently
     * similar to the new question. Exact matches are preferred over fuzzy matches.
     */
    @Synchronized
    fun recall(query: String, nowMs: Long = System.currentTimeMillis()): Hit? {
        val key = normalize(query)
        if (key.length < 3 || WebQueryRouter.needsCurrentInformation(query)) return null

        var changed = false
        val valid = readEntries().filter { entry ->
            val age = nowMs - entry.learnedAt
            val ttl = if (entry.volatile) VOLATILE_TTL_MS else EVERGREEN_TTL_MS
            val keep = entry.learnedAt > 0L && age in 0..ttl
            if (!keep) changed = true
            keep
        }
        if (changed) saveEntries(valid)

        val exact = valid.lastOrNull { it.key == key }
        if (exact != null) return exact.toHit()

        val best = valid.asSequence()
            .map { it to cosineLikeSimilarity(key, it.key) }
            .maxByOrNull { it.second }
            ?: return null
        val threshold = if (best.first.verified) VERIFIED_MATCH_THRESHOLD else SINGLE_SOURCE_MATCH_THRESHOLD
        return best.takeIf { it.second >= threshold }?.first?.toHit()
    }

    @Synchronized
    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    @Synchronized
    fun size(): Int = readEntries().size

    private fun Entry.toHit() = Hit(
        answer = answer,
        verified = verified,
        sourceCount = sources.size,
        learnedAt = learnedAt,
    )

    private fun readEntries(): List<Entry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val sourceArray = item.optJSONArray("sources") ?: JSONArray()
                Entry(
                    key = item.optString("key"),
                    answer = item.optString("answer"),
                    sources = List(sourceArray.length()) { sourceIndex -> sourceArray.optString(sourceIndex) }
                        .filter { it.startsWith("https://") }
                        .take(MAX_SOURCES),
                    verified = item.optBoolean("verified", false),
                    volatile = item.optBoolean("volatile", false),
                    learnedAt = item.optLong("learnedAt", 0L),
                )
            }.filter { it.key.isNotBlank() && it.answer.isNotBlank() && it.sources.isNotEmpty() }
        }.getOrDefault(emptyList())
    }

    private fun saveEntries(entries: List<Entry>) {
        val array = JSONArray()
        entries.takeLast(MAX_ENTRIES).forEach { entry ->
            val sources = JSONArray()
            entry.sources.take(MAX_SOURCES).forEach(sources::put)
            array.put(
                JSONObject()
                    .put("key", entry.key)
                    .put("answer", entry.answer)
                    .put("sources", sources)
                    .put("verified", entry.verified)
                    .put("volatile", entry.volatile)
                    .put("learnedAt", entry.learnedAt),
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        private const val PREFS = "niko_documental_knowledge_v1"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 160
        private const val MAX_SOURCES = 6
        private const val MAX_ANSWER_CHARS = 4_000
        private const val EVERGREEN_TTL_MS = 45L * 24 * 60 * 60 * 1_000
        private const val VOLATILE_TTL_MS = 6L * 60 * 60 * 1_000
        private const val VERIFIED_MATCH_THRESHOLD = 0.52
        private const val SINGLE_SOURCE_MATCH_THRESHOLD = 0.68

        internal fun normalize(text: String): String = MemoryLearning.key(text)
            .replace(Regex("\\b(?:niko|nico|nikko|nin|por favor|porfa)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(600)

        /** Weighted bag-of-words + character trigrams, small enough for on-device lookup. */
        internal fun cosineLikeSimilarity(left: String, right: String): Double {
            if (left == right) return 1.0
            fun vector(text: String): Map<String, Double> {
                val result = HashMap<String, Double>()
                val words = text.split(' ').filter { it.length > 1 }
                words.forEach { word ->
                    result["w:$word"] = (result["w:$word"] ?: 0.0) + 1.0
                    if (word.length >= 3) word.windowed(3).forEach { tri ->
                        result["c:$tri"] = (result["c:$tri"] ?: 0.0) + 0.18
                    }
                }
                return result
            }
            val a = vector(left)
            val b = vector(right)
            if (a.isEmpty() || b.isEmpty()) return 0.0
            var dot = 0.0
            a.forEach { (key, value) -> dot += value * (b[key] ?: 0.0) }
            val normA = sqrt(a.values.sumOf { it * it })
            val normB = sqrt(b.values.sumOf { it * it })
            return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
        }
    }
}
