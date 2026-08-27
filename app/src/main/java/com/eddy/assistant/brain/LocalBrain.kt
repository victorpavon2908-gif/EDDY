package com.eddy.assistant.brain

import java.text.Normalizer
import java.util.Locale

class LocalBrain {
    fun understand(input: String): AssistantCommand {
        val original = input.trim()
        val text = normalize(original)

        if (
            text.contains("olvida todo") ||
            text.contains("borra tu memoria") ||
            text.contains("borra lo que sabes de mi")
        ) {
            return AssistantCommand.ClearMemory
        }

        if (
            text.contains("que sabes de mi") ||
            text.contains("que has aprendido de mi") ||
            text.contains("que recuerdas de mi") ||
            text.contains("que hago mas") ||
            text.contains("que uso mas")
        ) {
            return AssistantCommand.MemorySummary
        }

        if (text.contains("que hora") || text.contains("hora es")) {
            return AssistantCommand.TellTime
        }

        parseMessage(original, text)?.let { return it }
        parseDial(text)?.let { return it }
        parseTimer(text)?.let { return it }
        parseAlarm(text)?.let { return it }
        parseMaps(original)?.let { return it }

        if (text.contains("camara") && containsAny(text, "abre", "abrir", "inicia", "enciende")) {
            return AssistantCommand.OpenCamera
        }

        val openRequested = containsAny(text, "abre", "abrir", "inicia", "lanza", "pon")
        if (openRequested) {
            when {
                text.contains("youtube") -> return AssistantCommand.OpenApp(SupportedApp.YOUTUBE)
                text.contains("whatsapp") || text.contains("wasa") -> return AssistantCommand.OpenApp(SupportedApp.WHATSAPP)
                text.contains("spotify") -> return AssistantCommand.OpenApp(SupportedApp.SPOTIFY)
                text.contains("maps") || text.contains("mapas") -> return AssistantCommand.OpenApp(SupportedApp.MAPS)
                text.contains("chrome") || text.contains("navegador") -> return AssistantCommand.OpenApp(SupportedApp.CHROME)
                text.contains("gmail") || text.contains("correo") -> return AssistantCommand.OpenApp(SupportedApp.GMAIL)
            }
        }

        if (
            text == "eddy" ||
            text.contains("hola") ||
            text.contains("como estas") ||
            text.contains("oye eddy") ||
            text.contains("buenos dias") ||
            text.contains("buenas tardes") ||
            text.contains("buenas noches")
        ) {
            return AssistantCommand.Greeting
        }

        return AssistantCommand.Unknown(original)
    }

    private fun parseDial(text: String): AssistantCommand.Dial? {
        if (!containsAny(text, "llama", "marcar", "marca", "llamar")) return null
        val match = Regex("""\+?\d[\d\s-]{6,}\d""").find(text) ?: return null
        return AssistantCommand.Dial(cleanPhone(match.value))
    }

    private fun parseMessage(original: String, normalized: String): AssistantCommand.ComposeMessage? {
        if (!containsAny(normalized, "mensaje", "sms")) return null
        if (!containsAny(normalized, "envia", "enviar", "manda", "mandar", "escribe")) return null

        val numberMatch = Regex("""\+?\d[\d\s-]{6,}\d""").find(normalized) ?: return null
        val body = Regex("""(?i)(?:diciendo|que diga|con el texto)\s+(.+)$""")
            .find(original)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

        return AssistantCommand.ComposeMessage(
            number = cleanPhone(numberMatch.value),
            message = body,
        )
    }

    private fun parseTimer(text: String): AssistantCommand.SetTimer? {
        if (!containsAny(text, "temporizador", "cronometro", "cuenta regresiva")) return null

        val hourMatch = Regex("""(\d+)\s*(?:hora|horas|h)\b""").find(text)
        val minuteMatch = Regex("""(\d+)\s*(?:minuto|minutos|min)\b""").find(text)
        val secondMatch = Regex("""(\d+)\s*(?:segundo|segundos|seg)\b""").find(text)

        val hours = hourMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = minuteMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val seconds = secondMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0

        var totalSeconds = hours * 3_600 + minutes * 60 + seconds

        if (totalSeconds == 0) {
            val bareNumber = Regex("""\b(\d{1,4})\b""").find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: return null
            totalSeconds = bareNumber * 60
        }

        if (totalSeconds !in 1..86_400) return null
        return AssistantCommand.SetTimer(
            seconds = totalSeconds,
            label = "Temporizador creado por EDDY",
        )
    }

    private fun parseAlarm(text: String): AssistantCommand.SetAlarm? {
        if (!containsAny(text, "alarma", "despiertame", "despertarme")) return null

        val match = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|a m|p m)?""").find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val meridiem = match.groupValues.getOrNull(3).orEmpty().replace(" ", "")

        val mentionsNight = text.contains("noche") || text.contains("tarde")
        val mentionsMorning = text.contains("manana") || text.contains("madrugada")

        if ((meridiem == "pm" || mentionsNight) && hour in 1..11) hour += 12
        if ((meridiem == "am" || mentionsMorning) && hour == 12) hour = 0

        if (hour !in 0..23 || minute !in 0..59) return null

        return AssistantCommand.SetAlarm(
            hour = hour,
            minute = minute,
            label = "Alarma creada por EDDY",
        )
    }

    private fun parseMaps(original: String): AssistantCommand.OpenMaps? {
        val phrases = listOf(
            "llévame a",
            "llevame a",
            "cómo llego a",
            "como llego a",
            "ruta a",
            "buscar en mapas",
            "busca en mapas",
            "mapa de",
        )

        val lower = original.lowercase(Locale.ROOT)
        for (phrase in phrases) {
            val index = lower.indexOf(phrase)
            if (index >= 0) {
                val destination = original.substring(index + phrase.length).trim(' ', ',', '.', '?', '¿')
                if (destination.isNotBlank()) return AssistantCommand.OpenMaps(destination)
            }
        }
        return null
    }

    private fun cleanPhone(value: String): String = value.replace(Regex("""[\s-]"""), "")

    private fun containsAny(text: String, vararg values: String): Boolean = values.any(text::contains)

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }
}
