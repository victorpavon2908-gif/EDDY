package com.niko.assistant.voice

import java.text.Normalizer
import java.util.Locale

/** Text echo guard, not acoustic echo cancellation or speaker identification. */
object LeoDuplexPolicy {
    private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    fun isEcho(heard: String, spoken: String): Boolean {
        val input = normalize(heard)
        val output = normalize(spoken)
        return input.isNotEmpty() && output.isNotEmpty() && " $output ".contains(" $input ")
    }

    fun canInterrupt(heard: String, spoken: String): Boolean {
        if (isEcho(heard, spoken)) return false
        val clean = normalize(heard)
        return Regex("^(?:oye |hey )?leo(?: |$)").containsMatchIn(clean) ||
            VoiceControl.parse(heard) == VoiceControl.STOP || clean == "no eso no"
    }
}
