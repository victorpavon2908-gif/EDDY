package com.eddy.assistant.voice

import java.text.Normalizer
import java.util.Locale

class WakeWordGate(
    private val wakeWord: String = "eddy",
    private val followUpWindowMs: Long = 20_000L,
) {
    private var armedUntil: Long = 0L

    fun arm(nowMs: Long = System.currentTimeMillis()) {
        armedUntil = nowMs + followUpWindowMs
    }

    fun isArmed(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs <= armedUntil

    fun consume(input: String, nowMs: Long = System.currentTimeMillis()): WakeResult {
        val raw = input.trim()
        if (raw.isBlank()) return WakeResult.Ignored

        val normalized = normalize(raw)
        if (hasWakeWordNormalized(normalized)) {
            arm(nowMs)
            val command = removeWakeWord(raw)
            return if (command.isBlank()) {
                WakeResult.Activated
            } else {
                armedUntil = 0L
                WakeResult.Command(command)
            }
        }

        if (isArmed(nowMs)) {
            armedUntil = 0L
            return WakeResult.Command(raw)
        }

        return WakeResult.Ignored
    }

    fun hasWakeWord(input: String): Boolean {
        val raw = input.trim()
        if (raw.isBlank()) return false
        return hasWakeWordNormalized(normalize(raw))
    }

    private fun hasWakeWordNormalized(normalized: String): Boolean {
        val escaped = Regex.escape(normalize(wakeWord))
        return Regex("(?:^|\\s|[,:;.!?¿¡-])$escaped(?:$|\\s|[,:;.!?¿¡-])").containsMatchIn(normalized)
    }

    private fun removeWakeWord(input: String): String {
        val escaped = Regex.escape(wakeWord)
        val regex = Regex("(?i)(?:^|(?<=\\s)|(?<=[,:;.!?¿¡-]))$escaped(?=$|\\s|[,:;.!?¿¡-])[,:;.!?¿¡\\s-]*")
        return input.replaceFirst(regex, "").trim()
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }
}

sealed interface WakeResult {
    data object Ignored : WakeResult
    data object Activated : WakeResult
    data class Command(val text: String) : WakeResult
}
