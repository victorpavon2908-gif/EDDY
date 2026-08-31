package com.eddy.assistant.voice

import java.text.Normalizer
import java.util.Locale

/**
 * Puerta estricta de activación de EDDY.
 *
 * En reposo, ningún texto se convierte en comando salvo que contenga la palabra exacta
 * "EDDY" como palabra independiente. No usamos alias fonéticos (edi/edy/eddie) porque
 * aumentaban activaciones falsas con conversaciones, TV o audio cercano.
 *
 * Una vez llamado EDDY, se abre una ventana corta de conversación para permitir respuestas
 * naturales sin repetir el nombre en cada frase. Al vencer esa ventana vuelve a modo pasivo.
 */
class WakeWordGate(
    private val wakeWord: String = "eddy",
    private val followUpWindowMs: Long = 20_000L,
    private val conversationWindowMs: Long = 12_000L,
) {
    private var armedUntil: Long = 0L
    private val strictWakeWord: String by lazy { normalize(wakeWord) }

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

        if (hasWakeWord(raw)) {
            val command = removeWakeWord(raw)
            return if (command.isBlank()) {
                arm(nowMs, followUpWindowMs)
                WakeResult.Activated
            } else {
                arm(nowMs, conversationWindowMs)
                WakeResult.Command(command)
            }
        }

        // Solo aceptamos una frase sin "EDDY" si la conversación YA fue activada por EDDY.
        if (isArmed(nowMs)) {
            arm(nowMs, conversationWindowMs)
            return WakeResult.Command(raw)
        }

        return WakeResult.Ignored
    }

    fun hasWakeWord(input: String): Boolean {
        val normalized = normalize(input.trim())
        if (normalized.isBlank()) return false
        return wakeRegex().containsMatchIn(normalized)
    }

    private fun wakeRegex(): Regex = Regex(
        "(?:^|\\s|[,:;.!?¿¡-])${Regex.escape(strictWakeWord)}(?:$|\\s|[,:;.!?¿¡-])",
    )

    private fun removeWakeWord(input: String): String {
        val regex = Regex(
            "(?i)(?:^|(?<=\\s)|(?<=[,:;.!?¿¡-]))${Regex.escape(strictWakeWord)}(?=$|\\s|[,:;.!?¿¡-])[,:;.!?¿¡\\s-]*",
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
