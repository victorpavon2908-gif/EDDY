package com.niko.assistant.voice

import java.text.Normalizer
import java.util.Locale

/** Text, fuzzy-lexical, and acoustic echo guard for full-duplex interruption (barge-in). */
object LeoDuplexPolicy {
    private val NATURAL_BARGE_IN = setOf(
        "no eso no",
        "espera",
        "esperate",
        "espera un momento",
        "un momento",
        "aguanta",
        "detente",
        "para",
        "ya basta",
        "cambiando de tema",
        "cambia de tema",
        "otra cosa",
        "silencio",
        "callate",
        "dejalo asi",
        "mejor no",
        "otra pregunta",
        "listo gracias",
        "escuchame",
    )

    private val FILLER_TOKENS = setOf("el", "la", "los", "las", "de", "del", "en", "y", "a", "un", "una", "es", "que")

    private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun isEcho(heard: String, spoken: String): Boolean {
        val input = normalize(heard)
        val output = normalize(spoken)
        if (input.isEmpty() || output.isEmpty()) return false
        if (" $output ".contains(" $input ")) return true

        // Fuzzy lexical echo guard for ASR-distorted speaker bleed (e.g., dropped articles or endings).
        val inputTokens = input.split(' ').filter { it.length >= 2 }
        if (inputTokens.size < 2) return false
        val outputTokens = output.split(' ').filter { it.length >= 2 }.toSet()
        if (outputTokens.isEmpty()) return false

        val contentInput = inputTokens.filterNot { it in FILLER_TOKENS }.ifEmpty { inputTokens }
        var matched = 0
        for (tok in contentInput) {
            if (tok in outputTokens || (tok.length >= 4 && outputTokens.any { it.startsWith(tok.take(4)) || tok.startsWith(it.take(4)) && kotlin.math.abs(it.length - tok.length) <= 2 })) {
                matched++
            }
        }
        val overlapRatio = matched.toDouble() / contentInput.size
        return overlapRatio >= 0.75 || (contentInput.size >= 3 && overlapRatio >= 0.66)
    }

    /**
     * Evalúa si la energía captada en el micrófono proviene del altavoz (eco acústico)
     * comparando RMS de entrada frente a reproducción, similitud espectral y detección de doble habla (DTD).
     */
    fun isAcousticEcho(micRms: Float, playbackRms: Float, spectralSimilarity: Float = 0f): Boolean {
        if (playbackRms <= 0.002f) return false
        // Double-Talk Detection (DTD): user speaking clearly louder than speaker playback with distinct spectrum.
        if (micRms > playbackRms * 1.45f && spectralSimilarity < 0.88f) return false
        if (spectralSimilarity >= 0.78f && micRms <= playbackRms * 1.35f) return true
        return micRms < playbackRms * 0.65f && spectralSimilarity >= 0.55f
    }

    fun canInterrupt(heard: String, spoken: String): Boolean {
        if (isEcho(heard, spoken)) return false
        val clean = normalize(heard)
        return Regex("^(?:oye |hola |hey )?leo(?: |$)").containsMatchIn(clean) ||
            VoiceControl.parse(heard) == VoiceControl.STOP ||
            clean in NATURAL_BARGE_IN
    }

    fun canInterruptFullDuplex(
        heard: String,
        spoken: String,
        micRms: Float,
        playbackRms: Float,
        spectralSimilarity: Float = 0f,
    ): Boolean {
        if (isAcousticEcho(micRms, playbackRms, spectralSimilarity)) return false
        return canInterrupt(heard, spoken)
    }
}
