package com.niko.assistant.learning

import android.content.Context
import com.niko.assistant.compat.UpgradeIdentity
import com.niko.assistant.memory.MemoryLearning
import org.json.JSONArray
import org.json.JSONObject

/** Bounded personal feedback, never labels a generated answer as true by itself. */
class LeoLearningJournal(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(UpgradeIdentity.memoryPreferences, Context.MODE_PRIVATE)
    data class Entry(val question: String, val answer: String, val time: Long, val feedback: String = "UNASSESSED")
    data class Stats(val interactions: Int, val useful: Int, val rejected: Int, val acceptedUpdates: Int, val rejectedUpdates: Int)

    fun record(question: String, answer: String, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        if (!AdaptiveLearningPolicy.canPersistLiteral(question) || !AdaptiveLearningPolicy.canPersistLiteral(answer)) return@synchronized
        if (question.isBlank() || answer.isBlank()) return@synchronized
        write((read() + Entry(question.take(600), answer.take(4000), now)).takeLast(128))
    }

    /** Only explicit assessment of a recent delivered answer. Silence is never approval. */
    fun feedback(positive: Boolean, now: Long = System.currentTimeMillis()): Entry? = synchronized(lock) {
        val entries = read().toMutableList()
        val previous = entries.lastOrNull() ?: return@synchronized null
        if (now - previous.time !in 0..(15 * 60 * 1000L)) return@synchronized null
        val updated = previous.copy(feedback = if (positive) "USEFUL" else "REJECTED")
        entries[entries.lastIndex] = updated
        write(entries)
        updated
    }

    fun recordTraining(accepted: Boolean) = synchronized(lock) {
        val key = if (accepted) "leo_training_accepted" else "leo_training_rejected"
        prefs.edit().putInt(key, (prefs.getInt(key, 0) + 1).coerceAtMost(1_000_000)).apply()
    }

    fun stats(): Stats = synchronized(lock) {
        val entries = read()
        Stats(entries.size, entries.count { it.feedback == "USEFUL" }, entries.count { it.feedback == "REJECTED" },
            prefs.getInt("leo_training_accepted", 0), prefs.getInt("leo_training_rejected", 0))
    }

    private fun read(): List<Entry> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (maxOf(0, array.length() - 128) until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Entry(item.getString("question"), item.getString("answer"), item.getLong("time"), item.optString("feedback", "UNASSESSED"))
        }
    }.getOrDefault(emptyList())

    private fun write(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("question", it.question).put("answer", it.answer).put("time", it.time).put("feedback", it.feedback)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "leo_learning_journal_v1"
        private val lock = Any()
        fun assessment(text: String): Boolean? = when (MemoryLearning.key(text).removePrefix("leo ").trim()) {
            "eso esta mal", "esa respuesta esta mal", "eso es incorrecto", "no me sirvio" -> false
            "eso me sirvio", "esa respuesta es correcta", "eso esta correcto", "eso esta bien" -> true
            else -> null
        }
        fun isStatusRequest(text: String): Boolean = MemoryLearning.key(text).removePrefix("leo ").trim() in setOf(
            "que aprendiste", "que has aprendido", "como va tu aprendizaje", "mostrame tu aprendizaje")
    }
}
