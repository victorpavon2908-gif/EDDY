package com.eddy.assistant.voice

import java.text.Normalizer
import java.util.Locale

class WakeWordGate(
    private val wakeWord: String = "eddy",
    private val followUpWindowMs: Long = 20_000L,
    private val conversationWindowMs: Long = 12_000L,
) {
    private var armedUntil: Long = 0L

    private val wakeAliases: Set<String> by lazy {
        setOf(
            normalize(wakeWord),
            "edi",
            "eddi",
            "eddie",
            "edy",
        )
    }

    fun arm(
        nowMs: Long = System.currentTimeMillis(),
        durationMs: Long = followUpWindowMs,
    ) {
        armedUntil = nowMs + durationMs.coerceAtLeast(500L)
    }

    fun disarm() {
        armedUntil = 0L
    }

    fun isArmed(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs <= armedUntil

    fun consume(input: String, nowMs: Long = System.currentTimeMillis()): WakeResult {
        val raw = input.trim()
        if (raw.isBlank()) return WakeResult.Ignored

        val normalized = normalize(raw)
        val alias = findWakeAlias(normalized)
        if (alias != null) {
            val command = removeWakeWord(raw)
            return if (command.isBlank()) {
                arm(nowMs, followUpWindowMs)
                WakeResult.Activated
            } else {
                arm(nowMs, conversationWindowMs)
                WakeResult.Command(command)
            }
        }

        if (isArmed(nowMs)) {
            arm(nowMs, conversationWindowMs)
            return WakeResult.Command(raw)
        }

        return WakeResult.Ignored
    }

    fun hasWakeWord(input: String): Boolean {
        val raw = input.trim()
        if (raw.isBlank()) return false
        return findWakeAlias(normalize(raw)) != null
    }

    private fun findWakeAlias(normalized: String): String? = wakeAliases.firstOrNull { alias ->
        Regex("(?:^|\\s|[,:;.!?¿¡-])${Regex.escape(alias)}(?:$|\\s|[,:;.!?¿¡-])")
            .containsMatchIn(normalized)
    }

    private fun removeWakeWord(input: String): String {
        val aliases = wakeAliases
            .sortedByDescending(String::length)
            .joinToString("|") { Regex.escape(it) }
        val regex = Regex(
            "(?i)(?:^|(?<=\\s)|(?<=[,:;.!?¿¡-]))(?:$aliases)(?=$|\\s|[,:;.!?¿¡-])[,:;.!?¿¡\\s-]*",
        )
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
