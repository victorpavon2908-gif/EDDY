package com.niko.assistant.memory

import java.text.Normalizer
import java.util.Locale

/** Only explicit, first-person statements teach durable local memory. */
object MemoryLearning {
    data class Lesson(val question: String, val answer: String)

    fun key(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9ñ ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun note(input: String): String? = Regex("(?i)^(?:record[aá]|recuerda|recordame|record[aá]me|recuerdame|recu[eé]rdame|aprend[eé]|aprende|guard[aá])\\s+que\\s+(.+)$")
        .matchEntire(input.trim())?.groupValues?.get(1)?.trim()?.take(500)?.takeIf { it.isNotBlank() }

    fun lesson(input: String): Lesson? {
        val match = Regex("(?i)^(?:aprend[eé] que )?cuando te pregunte\\s+(.+?),?\\s+(?:responde|respond[eé]|contesta|contest[aá])\\s+(.+)$")
            .matchEntire(input.trim()) ?: return null
        val question = match.groupValues[1].trim().trimEnd(',').take(240)
        val answer = match.groupValues[2].trim().take(1_000)
        return Lesson(question, answer).takeIf { key(question).length >= 3 && answer.isNotBlank() }
    }

    fun facts(input: String): Map<String, String> {
        val text = (note(input) ?: input).trim()
        if (text.contains('?') || text.startsWith('¿')) return emptyMap()
        val patterns = mapOf(
            "name" to "(?:me llamo|mi nombre es)", "likes" to "me gusta(?:n)?",
            "dislikes" to "no me gusta(?:n)?", "prefers" to "prefiero", "lives" to "vivo en",
            "work" to "trabajo (?:en|como)", "studies" to "estudio",
        )
        return patterns.mapNotNull { (key, prefix) ->
            Regex("(?i)^$prefix\\s+([^,.!?]{1,120})(?:[,.!].*)?$").matchEntire(text)
                ?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()
    }
}
