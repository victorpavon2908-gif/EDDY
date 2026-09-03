package com.niko.assistant.voice

import java.text.Normalizer
import java.util.Locale

/** Whole-utterance controls: quoted words, messages and device commands are not controls. */
enum class VoiceControl {
    STOP, DEACTIVATE;

    companion object {
        fun parse(input: String): VoiceControl? {
            var text = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^a-z0-9 ]"), " ")
                .replace(Regex("\\s+"), " ").trim()
            repeat(3) {
                text = text.replace(Regex("^(?:leo|niko|nico|por favor|porfa|oye|ey|yo quiero que|quiero que)\\s+"), "")
            }
            text = text.replace(Regex("\\s+(?:por favor|porfa|leo)$"), "").trim()
            return when (text) {
                "desactivate", "desactiva te", "te desactives", "apagate", "te apagues",
                "deja de escuchar", "desactiva la escucha", "desactiva el asistente" -> DEACTIVATE
                "para", "parate", "stop", "detente", "callate", "basta", "silencio",
                "cancela", "cancelalo", "cancela eso", "deja de hablar", "no sigas",
                "dejes de hablar", "pares", "te calles", "termina la respuesta" -> STOP
                else -> null
            }
        }
    }
}
