package com.niko.assistant.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Puerta de activación de LEO.
 *
 * LEO exige que el usuario lo llame antes de ejecutar o responder. Los alias internos
 * representan únicamente transcripciones habituales del mismo sonido "Leo".
 */
class WakeWordGate(
    private val wakeWord: String = "leo",
    private val followUpWindowMs: Long = 20_000L,
    private val conversationWindowMs: Long = 0L,
) {
    private var armedUntil: Long = 0L

    /**
     * Variantes prudentes que los motores pueden producir al escuchar "Leo".
     * No usamos "león" ni nombres más largos para evitar activaciones falsas.
     */
    private val wakeForms: Set<String> by lazy {
        setOf(
            normalize(wakeWord),
            "leo",
            "lio",
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
                finishCommand(nowMs)
                WakeResult.Command(command)
            }
        }

        if (isArmed(nowMs)) {
            finishCommand(nowMs)
            return WakeResult.Command(raw)
        }

        return WakeResult.Ignored
    }

    fun hasWakeWord(input: String): Boolean = findWakeForm(input) != null

    private fun finishCommand(nowMs: Long) {
        if (conversationWindowMs > 0L) arm(nowMs, conversationWindowMs) else disarm()
    }

    private fun findWakeForm(input: String): String? {
        val normalized = normalize(input.trim())
        if (normalized.isBlank()) return null
        return wakeForms.firstOrNull { form ->
            Regex("^(?:(?:hola|oye|hey|ey|ok|okay|por favor)[,:;.!?¿¡\\s-]+)*[¿¡]?${Regex.escape(form)}(?:$|\\s|[,:;.!?¿¡-])")
                .containsMatchIn(normalized)
        }
    }

    private fun removeWakeWord(input: String, matched: String): String {
        val regex = Regex(
            "(?i)^(?:(?:hola|oye|hey|ey|ok|okay|por favor)[,:;.!?¿¡\\s-]+)*[¿¡]?${Regex.escape(matched)}(?=$|\\s|[,:;.!?¿¡-])[,:;.!?¿¡\\s-]*",
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
