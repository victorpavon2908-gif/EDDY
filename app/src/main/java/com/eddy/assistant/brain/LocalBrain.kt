package com.eddy.assistant.brain

import java.text.Normalizer
import java.util.Locale

class LocalBrain {

    fun understand(input: String): AssistantCommand = understandSingle(input)

    fun understandMany(input: String): List<AssistantCommand> {
        val cleaned = cleanConversationalInput(input.trim())
        if (cleaned.isBlank()) return listOf(AssistantCommand.Unknown(input.trim()))
        val whole = understandSingle(cleaned)
        val pieces = splitActionClauses(cleaned)
        if (pieces.size <= 1) return listOf(whole)
        val parsed = pieces.map(::understandSingle)
        val knownCount = parsed.count { it !is AssistantCommand.Unknown }
        return if (knownCount >= 2) parsed.filter { it !is AssistantCommand.Unknown } else listOf(whole)
    }

    private fun understandSingle(input: String): AssistantCommand {
        val original = cleanConversationalInput(input.trim())
        val text = normalize(original)

        // Negated orders and questions about an action must never execute that action.
        if (Regex("^(?:no|nunca|jamas|tampoco|como|por que|explica|explicame|explicame)\\b").containsMatchIn(text)) {
            return AssistantCommand.Unknown(original)
        }

        if (containsAny(text, "olvida todo", "borra tu memoria", "borra lo que sabes de mi")) return AssistantCommand.ClearMemory
        if (containsAny(text, "que sabes de mi", "que has aprendido de mi", "que recuerdas de mi", "que hago mas", "que uso mas")) return AssistantCommand.MemorySummary
        if (text.contains("que hora") || text.contains("hora es")) return AssistantCommand.TellTime

        if (containsAny(text, "configura casa inteligente", "configurar casa inteligente", "configura domotica", "configurar domotica", "configura home assistant", "configurar home assistant")) return AssistantCommand.OpenSmartHomeSettings
        if (containsAny(text, "configura inteligencia", "configurar inteligencia", "configura ia", "configurar ia", "configura busqueda web", "configurar busqueda web", "configura internet de eddy")) return AssistantCommand.OpenAiSettings

        parseDynamicTool(text)?.let { return it }
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

        if (text.contains("bateria") && containsAny(text, "cuanta", "porcentaje", "nivel", "queda", "tengo")) return AssistantCommand.BatteryStatus
        if (containsAny(text, "vibra", "vibrar", "haz vibrar", "hace vibrar")) return AssistantCommand.Vibrate()
        if (text.contains("camara") && containsAny(text, "abre", "abri", "abrir", "abrime", "abreme", "inicia", "enciende", "encende")) return AssistantCommand.OpenCamera

        parseOpenApp(original, text)?.let { return it }
        if (Regex("^(?:eddy|edi|hola|como estas|oye eddy|buenos dias|buenas tardes|buenas noches)[.!?]*$").matches(text)) return AssistantCommand.Greeting
        return AssistantCommand.Unknown(original)
    }

    private fun parseDynamicTool(text: String): AssistantCommand? {
        if (
            containsAny(text, "vuelve a tu pantalla principal", "vuelve a la pantalla principal", "regresa a tu pantalla principal", "regresa a la pantalla principal", "vuelve a inicio", "regresa a inicio", "volver a eddy", "regresar a eddy")
        ) return AssistantCommand.OpenAppByName("EDDY_HOME")

        val wantsTransformation = containsAny(
            text,
            "convertite en", "conviertete en", "transformate en", "transforma tu pantalla en",
            "modo calculadora", "modo cronometro", "modo temporizador", "modo reloj", "modo notas", "modo conversor",
            "abre calculadora", "abri calculadora", "quiero calculadora",
            "abre cronometro", "abri cronometro", "quiero cronometro",
            "abre temporizador", "abri temporizador", "quiero temporizador",
            "abre reloj", "abri reloj", "quiero reloj",
            "abre notas", "abri notas", "quiero notas", "abre bloc de notas",
            "abre conversor", "abri conversor", "quiero conversor", "abre convertidor",
        )
        if (!wantsTransformation) return null
        return when {
            text.contains("calculadora") -> AssistantCommand.OpenAppByName("EDDY_TOOL_CALCULATOR")
            text.contains("cronometro") -> AssistantCommand.OpenAppByName("EDDY_TOOL_STOPWATCH")
            text.contains("temporizador") || text.contains("cuenta regresiva") -> AssistantCommand.OpenAppByName("EDDY_TOOL_TIMER")
            text.contains("reloj") -> AssistantCommand.OpenAppByName("EDDY_TOOL_CLOCK")
            text.contains("nota") || text.contains("bloc") -> AssistantCommand.OpenAppByName("EDDY_TOOL_NOTES")
            text.contains("conversor") || text.contains("convertidor") || text.contains("unidades") -> AssistantCommand.OpenAppByName("EDDY_TOOL_CONVERTER")
            else -> null
        }
    }

    private fun splitActionClauses(value: String): List<String> {
        val protectedMessage = normalize(value).contains("mensaje") && containsAny(normalize(value), "que diga", "diciendo", "con el texto", "con mensaje")
        if (protectedMessage) return listOf(value)
        return value
            .split(Regex("(?i)\\s*(?:,?\\s+(?:y\\s+despu[eé]s|despu[eé]s|luego|adem[aá]s|y\\s+luego|y))\\s+(?=(?:abre|abr[ií]|entra|enciende|encend[eé]|prende|apaga|llama|marca|pon|reproduce|sube|baja|activa|desactiva|busca|manda|env[ií]a|escribe|pon[eé]|quiero|necesito|haceme|hazme)\\b)"))
            .map { cleanConversationalInput(it).trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseOpenApp(original: String, normalized: String): AssistantCommand? {
        if (!containsAny(normalized, "abre", "abri", "abrir", "abrime", "abreme", "inicia", "lanza", "ejecuta", "entra a", "entra en", "metete en")) return null
        when {
            normalized.contains("youtube") -> return AssistantCommand.OpenApp(SupportedApp.YOUTUBE)
            normalized.contains("whatsapp") || normalized.contains("wasa") || normalized.contains("guasap") -> return AssistantCommand.OpenApp(SupportedApp.WHATSAPP)
            normalized.contains("spotify") -> return AssistantCommand.OpenApp(SupportedApp.SPOTIFY)
            normalized.contains("maps") || normalized.contains("mapas") -> return AssistantCommand.OpenApp(SupportedApp.MAPS)
            normalized.contains("chrome") || normalized.contains("navegador") -> return AssistantCommand.OpenApp(SupportedApp.CHROME)
            normalized.contains("gmail") || normalized.contains("correo") -> return AssistantCommand.OpenApp(SupportedApp.GMAIL)
            normalized.contains("lafise") || normalized.contains("lafice") -> return AssistantCommand.OpenAppByName("LAFISE")
        }
        val match = Regex("""(?i)(?:^|\b)(?:abre|abrí|abri|abrir|ábreme|abreme|abrime|inicia|lanza|ejecuta|entra(?:r)?(?:\s+a|\s+en)?|m[eé]tete\s+en)\s+(?:(?:la|el)\s+)?(?:(?:app|aplicación|aplicacion)\s+)?(.+)$""").find(original) ?: return null
        val name = stripPolitenessTail(match.groupValues.getOrNull(1).orEmpty())
        return name.takeIf { it.isNotBlank() }?.let(AssistantCommand::OpenAppByName)
    }

    private fun parseDial(text: String): AssistantCommand.Dial? {
        if (!containsAny(text, "llama", "llamame", "hacer una llamada", "haz una llamada", "haceme una llamada", "marcar", "marca", "llamar")) return null
        val match = PHONE_REGEX.find(text) ?: return null
        return AssistantCommand.Dial(cleanPhone(match.value))
    }

    private fun parseMessage(original: String, normalized: String): AssistantCommand.ComposeMessage? {
        if (!containsAny(normalized, "mensaje", "sms") || !containsAny(normalized, "envia", "enviar", "manda", "mandar", "escribe")) return null
        if (containsAny(normalized, "whatsapp", "wasa", "guasap")) return null
        val numberMatch = PHONE_REGEX.find(normalized) ?: return null
        return AssistantCommand.ComposeMessage(cleanPhone(numberMatch.value), extractMessageBody(original))
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
                .replace(PHONE_REGEX, " ")
                .replace(Regex("\\s+"), " ")
                .trim(' ', ',', '.', ':')
        }
        return AssistantCommand.WhatsAppMessage(number, body)
    }

    private fun extractMessageBody(original: String): String = Regex("""(?i)(?:diciendo|que diga|con el texto|con mensaje)\s+(.+)$""").find(original)?.groupValues?.getOrNull(1)?.trim().orEmpty()

    private fun parseSpotify(original: String, normalized: String): AssistantCommand.PlaySpotify? {
        if (!normalized.contains("spotify") || !containsAny(normalized, "pon", "pone", "reproduce", "toca", "escucha", "quiero escuchar", "musica")) return null
        var query = Regex("""(?i)(?:pon(?:e)?|poné|reproduce|toca|escucha|quiero escuchar)\s+(.+)$""").find(original)?.groupValues?.getOrNull(1).orEmpty()
        query = query.replace(Regex("(?i)\\s+(?:en|por)\\s+spotify\\s*$"), "").replace(Regex("(?i)^spotify\\s+"), "").trim(' ', ',', '.')
        if (query.equals("spotify", true)) query = ""
        return AssistantCommand.PlaySpotify(query)
    }

    private fun parseTorch(text: String): AssistantCommand.SetTorch? {
        if (!containsAny(text, "linterna", "flash del telefono", "luz del telefono", "flash")) return null
        return when {
            containsAny(text, "apaga", "apagar", "apagame", "apagala", "desactiva", "quita", "desenciende") -> AssistantCommand.SetTorch(false)
            containsAny(text, "enciende", "encender", "encende", "encendeme", "prende", "prender", "prendeme", "activa", "activar", "pon la linterna") -> AssistantCommand.SetTorch(true)
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
        if (!containsAny(text, "abre", "abri", "abrir", "abrime", "abreme", "activa", "activar", "enciende", "encende", "prende", "configura", "ajustes")) return null
        return when {
            text.contains("wifi") || text.contains("wi fi") -> AssistantCommand.OpenSystemPanel(SystemPanel.WIFI)
            text.contains("bluetooth") -> AssistantCommand.OpenSystemPanel(SystemPanel.BLUETOOTH)
            text.contains("internet") || text.contains("datos moviles") -> AssistantCommand.OpenSystemPanel(SystemPanel.INTERNET)
            text.contains("ubicacion") || text.contains("gps") -> AssistantCommand.OpenSystemPanel(SystemPanel.LOCATION)
            text.contains("nfc") -> AssistantCommand.OpenSystemPanel(SystemPanel.NFC)
            text.contains("modo avion") -> AssistantCommand.OpenSystemPanel(SystemPanel.AIRPLANE)
            text.contains("configuracion") || text.contains("ajustes") -> AssistantCommand.OpenSystemPanel(SystemPanel.SETTINGS)
            else -> null
        }
    }

    private fun parseSmartHome(text: String): AssistantCommand.SmartHomeControl? {
        if (!containsAny(text, "luz", "lampara", "bombillo", "foco", "ventilador", "abanico", "enchufe", "tomacorriente", "switch", "televisor", "tv")) return null
        val enabled = when {
            containsAny(text, "apaga", "apagar", "apagame", "desactiva") -> false
            containsAny(text, "enciende", "encender", "encende", "prende", "prender", "activa") -> true
            else -> return null
        }
        val target = text.replace(Regex("\\b(?:enciende|encender|encende|prende|prender|activa|apaga|apagar|apagame|desactiva)\\b"), " ").replace(Regex("\\b(?:la|el|los|las|de|del)\\b"), " ").replace(Regex("\\s+"), " ").trim()
        return target.takeIf { it.isNotBlank() }?.let { AssistantCommand.SmartHomeControl(it, enabled) }
    }

    private fun parseWebSearch(original: String): AssistantCommand.SearchWeb? {
        val normalized = normalize(original)
        val phrases = listOf("busca en internet", "buscar en internet", "busca en google", "investiga en internet", "consulta en internet", "googlea", "busca", "investiga", "averigua")
        for (phrase in phrases) {
            val match = Regex("(?:^|\\s)${Regex.escape(phrase)}(?:\\s+|$)").find(normalized) ?: continue
            val query = normalized.substring(match.range.last + 1).trim(' ', ',', '.', '?', '¿', ':')
            if (query.isNotBlank()) return AssistantCommand.SearchWeb(query)
        }
        return null
    }

    private fun parseShare(original: String, text: String): AssistantCommand.ShareText? {
        if (!containsAny(text, "comparte", "comparti", "compartir")) return null
        val match = Regex("""(?i)(?:comparte|comparti|compartir)\s+(.+)$""").find(original) ?: return null
        return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let(AssistantCommand::ShareText)
    }

    private fun parseTimer(text: String): AssistantCommand.SetTimer? {
        if (!containsAny(text, "temporizador", "cronometro", "cuenta regresiva")) return null
        val hours = Regex("""(\d+)\s*(?:hora|horas|h)\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val minutes = Regex("""(\d+)\s*(?:minuto|minutos|min)\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        val seconds = Regex("""(\d+)\s*(?:segundo|segundos|seg)\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        var total = hours * 3600 + minutes * 60 + seconds
        if (total == 0) total = (Regex("""\b(\d{1,4})\b""").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return null) * 60
        if (total !in 1..86_400) return null
        return AssistantCommand.SetTimer(total, "Temporizador creado por EDDY")
    }

    private fun parseAlarm(text: String): AssistantCommand.SetAlarm? {
        if (!containsAny(text, "alarma", "despiertame", "despertarme")) return null
        val match = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm|a m|p m)?""").find(text) ?: return null
        var hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        val meridiem = match.groupValues.getOrNull(3).orEmpty().replace(" ", "")
        if ((meridiem == "pm" || text.contains("noche") || text.contains("tarde")) && hour in 1..11) hour += 12
        if ((meridiem == "am" || text.contains("manana") || text.contains("madrugada")) && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return AssistantCommand.SetAlarm(hour, minute, "Alarma creada por EDDY")
    }

    private fun parseMaps(original: String): AssistantCommand.OpenMaps? {
        val normalized = normalize(original)
        for (phrase in listOf("llevame a", "como llego a", "ruta a", "buscar en mapas", "busca en mapas", "mapa de")) {
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
        repeat(8) {
            val updated = current.replace(CONVERSATIONAL_PREFIX, "").replace(FILLER_PREFIX, "").trim(' ', ',', '.', ':', ';', '-', '¿', '?', '¡', '!')
            if (updated == current) return current
            current = updated
        }
        return current
    }

    private fun stripPolitenessTail(value: String): String = value.replace(Regex("(?i)\\s+(?:por favor|porfa|si podes|si pod[eé]s|haceme el favor)\\s*$"), "").trim(' ', ',', '.', '!', '?')
    private fun cleanPhone(value: String): String = value.replace(Regex("[\\s-]"), "")
    private fun containsAny(text: String, vararg values: String): Boolean = values.any(text::contains)
    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "").replace(Regex("\\s+"), " ").trim()

    companion object {
        private val PHONE_REGEX = Regex("""\+?\d[\d\s-]{6,}\d""")
        private val FILLER_PREFIX = Regex("""(?i)^(?:este+|eh+|em+|mmm+|mira|fijate|bueno|a ver)\b\s*[,.:;!-]*\s*""")
        private val CONVERSATIONAL_PREFIX = Regex("""(?i)^(?:(?:eddy|eddi|eddie|edy|edi)\s*[,.:;!¿?¡-]*\s*)?(?:(?:por\s+favor|porfa|haceme\s+el\s+favor(?:\s+de)?|hazme\s+el\s+favor(?:\s+de)?|me\s+haces\s+el\s+favor(?:\s+de)?|me\s+hac[eé]s\s+el\s+favor(?:\s+de)?|me\s+pod[eé]s|pod[eé]s|podr[ií]as|quiero\s+que|necesito\s+que|te\s+pido\s+que|dale|oye|ey|hey|mira|mir[aá])\s+)+""")
    }
}
