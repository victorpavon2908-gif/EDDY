package com.eddy.assistant.brain

import java.text.Normalizer
import java.util.Locale

class LocalBrain {
    fun understand(input: String): AssistantCommand {
        val original = cleanConversationalInput(input.trim())
        val text = normalize(original)

        if (
            text.contains("olvida todo") ||
            text.contains("borra tu memoria") ||
            text.contains("borra lo que sabes de mi")
        ) return AssistantCommand.ClearMemory

        if (
            text.contains("que sabes de mi") ||
            text.contains("que has aprendido de mi") ||
            text.contains("que recuerdas de mi") ||
            text.contains("que hago mas") ||
            text.contains("que uso mas")
        ) return AssistantCommand.MemorySummary

        if (text.contains("que hora") || text.contains("hora es")) return AssistantCommand.TellTime

        if (
            text.contains("configura casa inteligente") ||
            text.contains("configurar casa inteligente") ||
            text.contains("configura domotica") ||
            text.contains("configurar domotica") ||
            text.contains("configura home assistant") ||
            text.contains("configurar home assistant")
        ) return AssistantCommand.OpenSmartHomeSettings

        if (
            text.contains("configura inteligencia") ||
            text.contains("configurar inteligencia") ||
            text.contains("configura ia") ||
            text.contains("configurar ia") ||
            text.contains("configura busqueda web") ||
            text.contains("configurar busqueda web") ||
            text.contains("configura internet de eddy")
        ) return AssistantCommand.OpenAiSettings

        parseWhatsApp(original, text)?.let { return it }
        parseMessage(original, text)?.let { return it }
        parseDial(text)?.let { return it }
        parseSpotify(original, text)?.let { return it }
        parseTimer(text)?.let { return it }
        parseAlarm(text)?.let { return it }
        parseMaps(original)?.let { return it }
        parseTorch(text)?.let { return it }
        parseVolume(text)?.let { return it }
        parseBrightness(text)?.let { return it }
        parseSystemPanel(text)?.let { return it }
        parseSmartHome(text)?.let { return it }
        parseWebSearch(original)?.let { return it }
        parseShare(original, text)?.let { return it }

        if (
            text.contains("bateria") &&
            containsAny(text, "cuanta", "porcentaje", "nivel", "queda", "tengo")
        ) return AssistantCommand.BatteryStatus

        if (containsAny(text, "vibra", "vibrar", "haz vibrar", "hace vibrar")) {
            return AssistantCommand.Vibrate()
        }

        if (
            text.contains("camara") &&
            containsAny(text, "abre", "abri", "abrir", "abrime", "abreme", "inicia", "enciende", "encende")
        ) return AssistantCommand.OpenCamera

        parseOpenApp(original, text)?.let { return it }

        if (
            text == "eddy" ||
            text == "edi" ||
            text.contains("hola") ||
            text.contains("como estas") ||
            text.contains("oye eddy") ||
            text.contains("buenos dias") ||
            text.contains("buenas tardes") ||
            text.contains("buenas noches")
        ) return AssistantCommand.Greeting

        return AssistantCommand.Unknown(original)
    }

    private fun parseOpenApp(original: String, normalized: String): AssistantCommand? {
        val openRequested = containsAny(
            normalized,
            "abre", "abri", "abrir", "abrime", "abreme", "abras",
            "inicia", "lanza", "ejecuta", "entra a", "entra en", "entrar a", "entrar en", "metete en",
        )
        if (!openRequested) return null

        when {
            normalized.contains("youtube") -> return AssistantCommand.OpenApp(SupportedApp.YOUTUBE)
            normalized.contains("whatsapp") || normalized.contains("wasa") -> return AssistantCommand.OpenApp(SupportedApp.WHATSAPP)
            normalized.contains("spotify") -> return AssistantCommand.OpenApp(SupportedApp.SPOTIFY)
            normalized.contains("maps") || normalized.contains("mapas") -> return AssistantCommand.OpenApp(SupportedApp.MAPS)
            normalized.contains("chrome") || normalized.contains("navegador") -> return AssistantCommand.OpenApp(SupportedApp.CHROME)
            normalized.contains("gmail") || normalized.contains("correo") -> return AssistantCommand.OpenApp(SupportedApp.GMAIL)
            normalized.contains("lafise") || normalized.contains("lafice") -> return AssistantCommand.OpenAppByName("LAFISE")
        }

        val match = Regex(
            """(?i)(?:^|\b)(?:abre|abrí|abri|abrir|ábreme|abreme|abrime|abras|inicia|lanza|ejecuta|entra(?:r)?(?:\s+a|\s+en)?|m[eé]tete\s+en)\s+(?:(?:la|el)\s+)?(?:(?:app|aplicación|aplicacion)\s+)?(.+)$""",
        ).find(original) ?: return null

        val name = match.groupValues.getOrNull(1)
            ?.replace(Regex("(?i)\\s+por favor$"), "")
            ?.trim(' ', ',', '.', '!', '?')
            .orEmpty()

        return name.takeIf { it.isNotBlank() }?.let(AssistantCommand::OpenAppByName)
    }

    private fun parseDial(text: String): AssistantCommand.Dial? {
        if (!containsAny(text, "llama", "marcar", "marca", "llamar")) return null
        val match = PHONE_REGEX.find(text) ?: return null
        return AssistantCommand.Dial(cleanPhone(match.value))
    }

    private fun parseMessage(original: String, normalized: String): AssistantCommand.ComposeMessage? {
        if (!containsAny(normalized, "mensaje", "sms")) return null
        if (!containsAny(normalized, "envia", "enviar", "manda", "mandar", "escribe")) return null
        if (normalized.contains("whatsapp") || normalized.contains("wasa") || normalized.contains("guasap")) return null

        val numberMatch = PHONE_REGEX.find(normalized) ?: return null
        return AssistantCommand.ComposeMessage(
            number = cleanPhone(numberMatch.value),
            message = extractMessageBody(original),
        )
    }

    private fun parseWhatsApp(original: String, normalized: String): AssistantCommand.WhatsAppMessage? {
        if (!containsAny(normalized, "whatsapp", "wasa", "guasap")) return null
        if (!containsAny(normalized, "envia", "enviar", "manda", "mandar", "escribe", "mensaje")) return null

        val number = PHONE_REGEX.find(normalized)?.value?.let(::cleanPhone)
        var body = extractMessageBody(original)
        if (body.isBlank()) {
            body = normalize(original)
                .replace(Regex("\\b(?:envia|enviar|manda|mandar|escribe|mensaje)\\b"), " ")
                .replace(Regex("\\b(?:por|en)?\\s*(?:whatsapp|wasa|guasap)\\b"), " ")
                .replace(Regex("\\b(?:al|a)\\b"), " ")
                .replace(PHONE_REGEX, " ")
                .replace(Regex("\\s+"), " ")
                .trim(' ', ',', '.', ':')
        }
        return AssistantCommand.WhatsAppMessage(number = number, message = body)
    }

    private fun extractMessageBody(original: String): String =
        Regex("""(?i)(?:diciendo|que diga|con el texto|con mensaje)\s+(.+)$""")
            .find(original)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    private fun parseSpotify(original: String, normalized: String): AssistantCommand.PlaySpotify? {
        if (!normalized.contains("spotify")) return null
        if (!containsAny(normalized, "pon", "pone", "reproduce", "toca", "escucha", "quiero escuchar", "musica")) return null

        var query = Regex("""(?i)(?:pon(?:e)?|poné|reproduce|toca|escucha|quiero escuchar)\s+(.+)$""")
            .find(original)?.groupValues?.getOrNull(1).orEmpty()
        query = query
            .replace(Regex("(?i)\\s+(?:en|por)\\s+spotify\\s*$"), "")
            .replace(Regex("(?i)^spotify\\s+"), "")
            .trim(' ', ',', '.')
        if (query.equals("spotify", ignoreCase = true)) query = ""
        return AssistantCommand.PlaySpotify(query)
    }

    private fun parseTorch(text: String): AssistantCommand.SetTorch? {
        if (!containsAny(text, "linterna", "flash del telefono", "luz del telefono")) return null
        return when {
            containsAny(text, "apaga", "apagar", "apagame", "apagala", "desactiva", "quita") -> AssistantCommand.SetTorch(false)
            containsAny(
                text,
                "enciende", "encender", "encende", "encendeme",
                "prende", "prender", "prendeme", "activa", "activar",
            ) -> AssistantCommand.SetTorch(true)
            else -> null
        }
    }

    private fun parseVolume(text: String): AssistantCommand? {
        if (!containsAny(text, "volumen", "sonido")) return null
        val percent = Regex("""\b(100|[1-9]?\d)\s*%""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (percent != null) return AssistantCommand.SetVolume(percent)
        return when {
            containsAny(text, "silencio", "mutea", "mute", "quita el sonido") -> AssistantCommand.AdjustVolume(VolumeDirection.MUTE)
            containsAny(text, "sube", "subi", "subir", "aumenta", "mas volumen") -> AssistantCommand.AdjustVolume(VolumeDirection.UP)
            containsAny(text, "baja", "bajar", "disminuye", "menos volumen") -> AssistantCommand.AdjustVolume(VolumeDirection.DOWN)
            else -> null
        }
    }

    private fun parseBrightness(text: String): AssistantCommand.SetBrightness? {
        if (!containsAny(text, "brillo", "luminosidad")) return null
        val percent = Regex("""\b(100|[1-9]?\d)\s*%""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
        return AssistantCommand.SetBrightness(percent)
    }

    private fun parseSystemPanel(text: String): AssistantCommand.OpenSystemPanel? {
        val wantsControl = containsAny(
            text,
            "abre", "abri", "abrir", "abrime", "abreme", "activa", "activar", "enciende", "encende", "encender", "prende", "configura", "ajustes",
        )
        if (!wantsControl) return null
        return when {
            text.contains("wifi") || text.contains("wi fi") -> AssistantCommand.OpenSystemPanel(SystemPanel.WIFI)
            text.contains("bluetooth") -> AssistantCommand.OpenSystemPanel(SystemPanel.BLUETOOTH)
            text.contains("internet") || text.contains("datos moviles") -> AssistantCommand.OpenSystemPanel(SystemPanel.INTERNET)
            text.contains("ubicacion") || text.contains("gps") -> AssistantCommand.OpenSystemPanel(SystemPanel.LOCATION)
            text.contains("nfc") -> AssistantCommand.OpenSystemPanel(SystemPanel.NFC)
            text.contains("modo avion") || text.contains("avion") -> AssistantCommand.OpenSystemPanel(SystemPanel.AIRPLANE)
            text.contains("configuracion") || text.contains("ajustes") -> AssistantCommand.OpenSystemPanel(SystemPanel.SETTINGS)
            else -> null
        }
    }

    private fun parseSmartHome(text: String): AssistantCommand.SmartHomeControl? {
        val hasTarget = containsAny(
            text,
            "luz", "lampara", "bombillo", "foco", "ventilador", "abanico", "enchufe", "tomacorriente", "switch", "televisor", "tv",
        )
        if (!hasTarget) return null
        val enabled = when {
            containsAny(text, "apaga", "apagar", "apagame", "desactiva") -> false
            containsAny(text, "enciende", "encender", "encende", "prende", "prender", "activa") -> true
            else -> return null
        }
        val target = text
            .replace(Regex("\\b(?:enciende|encender|encende|prende|prender|activa|apaga|apagar|apagame|desactiva)\\b"), " ")
            .replace(Regex("\\b(?:la|el|los|las|de|del)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ',', '.')
        if (target.isBlank()) return null
        return AssistantCommand.SmartHomeControl(target = target, enabled = enabled)
    }

    private fun parseWebSearch(original: String): AssistantCommand.SearchWeb? {
        val normalized = normalize(original)
        val phrases = listOf(
            "busca en internet", "buscar en internet", "busca en google", "buscar en google",
            "investiga en internet", "investigar en internet", "consulta en internet", "googlea",
            "busca", "buscar", "investiga", "investigar", "averigua", "averiguar",
        )
        for (phrase in phrases) {
            val regex = Regex("(?:^|\\s)${Regex.escape(phrase)}(?:\\s+|$)")
            val match = regex.find(normalized) ?: continue
            val query = normalized.substring(match.range.last + 1).trim(' ', ',', '.', '?', '¿', ':')
            if (query.isNotBlank()) return AssistantCommand.SearchWeb(query)
        }
        return null
    }

    private fun parseShare(original: String, text: String): AssistantCommand.ShareText? {
        if (!containsAny(text, "comparte", "comparti", "compartir")) return null
        val normalized = normalize(original)
        val match = Regex("""(?:comparte|comparti|compartir)\s+(.+)$""").find(normalized) ?: return null
        val value = match.groupValues.getOrNull(1)?.trim().orEmpty()
        return value.takeIf { it.isNotBlank() }?.let(AssistantCommand::ShareText)
    }

    private fun parseTimer(text: String): AssistantCommand.SetTimer? {
        if (!containsAny(text, "temporizador", "cronometro", "cuenta regresiva")) return null
        val hours = Regex("""(\d+)\s*(?:hora|horas|h)\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = Regex("""(\d+)\s*(?:minuto|minutos|min)\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val seconds = Regex("""(\d+)\s*(?:segundo|segundos|seg)\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        var totalSeconds = hours * 3_600 + minutes * 60 + seconds
        if (totalSeconds == 0) {
            val bareNumber = Regex("""\b(\d{1,4})\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null
            totalSeconds = bareNumber * 60
        }
        if (totalSeconds !in 1..86_400) return null
        return AssistantCommand.SetTimer(totalSeconds, "Temporizador creado por EDDY")
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
        return AssistantCommand.SetAlarm(hour, minute, "Alarma creada por EDDY")
    }

    private fun parseMaps(original: String): AssistantCommand.OpenMaps? {
        val normalized = normalize(original)
        val phrases = listOf("llevame a", "como llego a", "ruta a", "buscar en mapas", "busca en mapas", "mapa de")
        for (phrase in phrases) {
            val index = normalized.indexOf(phrase)
            if (index >= 0) {
                val destination = normalized.substring(index + phrase.length).trim(' ', ',', '.', '?', '¿')
                if (destination.isNotBlank()) return AssistantCommand.OpenMaps(destination)
            }
        }
        return null
    }

    private fun cleanConversationalInput(value: String): String {
        var current = value.trim()
        repeat(5) {
            val updated = current
                .replace(CONVERSATIONAL_PREFIX, "")
                .trim(' ', ',', '.', ':', ';', '-', '¿', '?', '¡', '!')
            if (updated == current) return current
            current = updated
        }
        return current
    }

    private fun cleanPhone(value: String): String = value.replace(Regex("""[\s-]"""), "")
    private fun containsAny(text: String, vararg values: String): Boolean = values.any(text::contains)

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.ROOT)
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }

    companion object {
        private val PHONE_REGEX = Regex("""\+?\d[\d\s-]{6,}\d""")
        private val CONVERSATIONAL_PREFIX = Regex(
            """(?i)^(?:(?:eddy|eddi|eddie|edy|edi)\s*[,.:;!¿?¡-]*\s*)?(?:por\s+favor|haceme\s+el\s+favor(?:\s+de)?|hazme\s+el\s+favor(?:\s+de)?|me\s+haces\s+el\s+favor(?:\s+de)?|me\s+hacés\s+el\s+favor(?:\s+de)?|me\s+pod[eé]s|pod[eé]s|podr[ií]as|quiero\s+que|necesito\s+que|te\s+pido\s+que|dale|oye|ey|hey)\s+""",
        )
    }
}
