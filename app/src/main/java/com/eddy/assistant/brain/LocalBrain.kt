package com.eddy.assistant.brain

import java.text.Normalizer
import java.util.Locale

class LocalBrain {
    fun understand(input: String): AssistantCommand {
        val text = normalize(input)

        if (text.contains("que hora") || text.contains("hora es")) {
            return AssistantCommand.TellTime
        }

        if (text.contains("camara") && (text.contains("abre") || text.contains("abrir"))) {
            return AssistantCommand.OpenCamera
        }

        val openRequested = text.contains("abre") || text.contains("abrir") || text.contains("inicia")
        if (openRequested) {
            when {
                text.contains("youtube") -> return AssistantCommand.OpenApp(SupportedApp.YOUTUBE)
                text.contains("whatsapp") || text.contains("wasa") -> return AssistantCommand.OpenApp(SupportedApp.WHATSAPP)
                text.contains("spotify") -> return AssistantCommand.OpenApp(SupportedApp.SPOTIFY)
            }
        }

        if (
            text.contains("hola") ||
            text.contains("como estas") ||
            text == "eddy" ||
            text.contains("oye eddy")
        ) {
            return AssistantCommand.Greeting
        }

        return AssistantCommand.Unknown(input)
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.getDefault())
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }
}
