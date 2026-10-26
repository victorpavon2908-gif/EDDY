package com.niko.assistant.voice

import java.text.Normalizer
import java.util.Locale

/** Text and acoustic echo guard for full-duplex interruption (barge-in). */
object LeoDuplexPolicy {
    private val NATURAL_BARGE_IN = setOf(
        "no eso no",
        "espera",
        "esperate",
        "espera un momento",
        "un momento",
        "cambiando de tema",
        "silencio",
        "callate",
        "dejalo asi",
        "mejor no",
        "otra pregunta",
    )

    private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun isEcho(heard: String, spoken: String): Boolean {
        val input = normalize(heard)
        val output = normalize(spoken)
        return input.isNotEmpty() && output.isNotEmpty() && " $output ".contains(" $input ")
    }

    /**
     * Evalúa si la energía captada en el micrófono proviene del altavoz (eco acústico)
     * comparando RMS de entrada frente a reproducción y similitud espectral.
     */
    fun isAcousticEcho(micRms: Float, playbackRms: Float, spectralSimilarity: Float = 0f): Boolean {
        if (playbackRms <= 0.002f) return false
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
