package com.eddy.assistant.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Puerta de activación de EDDY.
 *
 * EDDY sigue exigiendo que el usuario lo llame antes de ejecutar o responder. Los alias
 * de esta clase NO son palabras alternativas de activación: representan transcripciones
 * habituales del mismo sonido "EDDY" producidas por distintos motores ASR (edi/edy/eddie).
 * Esto evita que una pronunciación correcta se pierda solo porque Android la escribió distinto.
 */
class WakeWordGate(
    private val wakeWord: String = "eddy",
    private val followUpWindowMs: Long = 20_000L,
    private val conversationWindowMs: Long = 12_000L,
) {
    private var armedUntil: Long = 0L

    private val wakeForms: Set<String> by lazy {
        setOf(normalize(wakeWord), "edi", "edy", "eddi", "eddie")
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

    fun isArmed(nowMs: Long = System.currentTimeMillis()): Boolean =
        armedUntil > 0L && nowMs <= armedUntil

    fun consume(input: String, nowMs: Long = System.currentTimeMillis()): WakeResult {
        val raw = input.trim()
        if (raw.isBlank()) return WakeResult.Ignored

        val matched = findWakeForm(raw)
        if (matched != null) {
            val command = removeWakeWord(raw, matched)
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

    fun hasWakeWord(input: String): Boolean = findWakeForm(input) != null

    private fun findWakeForm(input: String): String? {
        val normalized = normalize(input.trim())
        if (normalized.isBlank()) return null
        return wakeForms.firstOrNull { form ->
            Regex("(?:^|\\s|[,:;.!?¿¡-])${Regex.escape(form)}(?:$|\\s|[,:;.!?¿¡-])")
                .containsMatchIn(normalized)
        }
    }

    private fun removeWakeWord(input: String, matched: String): String {
        val regex = Regex(
            "(?i)(?:^|(?<=\\s)|(?<=[,:;.!?¿¡-]))${Regex.escape(matched)}(?=$|\\s|[,:;.!?¿¡-])[,:;.!?¿¡\\s-]*",
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
