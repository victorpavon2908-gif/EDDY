package com.niko.assistant.brain

import com.niko.assistant.devicecontrol.NikoVisualContext
import com.niko.assistant.devicecontrol.NikoUiTaskPolicy
import java.text.Normalizer
import java.util.Locale

/**
 * Capa semántica segura para órdenes naturales.
 *
 * El parser determinista sigue siendo la ruta rápida. Solo cuando no entiende una
 * petición usamos el LLM local para traducir lenguaje libre a un DSL cerrado. El
 * resultado nunca se ejecuta como texto/código: únicamente se aceptan acciones de
 * esta lista y cada argumento se vuelve a validar antes de crear AssistantCommand.
 */
class NikoSemanticActionResolver(
    private val brain: LocalBrain,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val structuredCompletion: suspend (String) -> String?,
) {
    private var lastActionContext: String = ""
    private var lastActionAt: Long = 0L

    suspend fun resolveMany(text: String): List<AssistantCommand> {
        val original = text.trim()
        if (original.isBlank()) return listOf(AssistantCommand.Unknown(text))

        val deterministic = brain.understandMany(original).map { command ->
            if (command is AssistantCommand.ComposeMessage &&
                !Regex("\\b(?:sms|mensajes de texto)\\b").containsMatchIn(key(original)) &&
                recentContext().contains("whatsapp", ignoreCase = true)
            ) AssistantCommand.WhatsAppMessage(command.number.takeIf(String::isNotBlank), command.message) else command
        }
        if (deterministic.hasKnownAction()) {
            remember(deterministic)
            return deterministic
        }

        // Preguntas como "mirá esto" o "qué dice esta pantalla" pertenecen a la
        // conversación visual local. Nunca deben convertirse en CAMERA/CLICK/etc.
        if (NikoVisualContext.wantsScreenContext(original)) return deterministic

        // Una pregunta sobre una acción o una orden negada debe seguir siendo conversación.
        // El LLM semántico no puede convertir "no abras YouTube" o "cómo apago esto" en ejecución.
        if (isProtectedNonAction(original)) return deterministic

        // Segunda pasada barata: quita cortesía/conectores iniciales, sin destruir
        // nombres de apps ni el contenido de mensajes.
        val simplified = simplifyPoliteLead(original)
        if (simplified != original) {
            val retried = brain.understandMany(simplified)
            if (retried.hasKnownAction()) {
                remember(retried)
                return retried
            }
        }

        contextualShortcut(original)?.let { command ->
            val result = listOf(command)
            remember(result)
            return result
        }

        val context = recentContext()
        val raw = structuredCompletion(buildPrompt(original, context)) ?: return deterministic
        val semantic = parseDsl(raw)
        if (semantic.isEmpty()) return deterministic
        remember(semantic)
        return semantic
    }

    /** Public for tests: strict parser for the local model's allow-listed action DSL. */
    fun parseDsl(value: String): List<AssistantCommand> {
        val commands = value
            .replace("```", "")
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(MAX_ACTIONS)
            .mapNotNull(::parseLine)
            .toList()
        return commands.takeIf { it.isNotEmpty() } ?: emptyList()
    }

    private fun parseLine(line: String): AssistantCommand? {
        val parts = line.split('|').map(String::trim)
        val opcode = parts.firstOrNull()?.uppercase(Locale.ROOT) ?: return null
        fun arg(index: Int): String? = parts.getOrNull(index)?.takeIf { it.isNotBlank() }
        fun percent(index: Int): Int? = arg(index)?.toIntOrNull()?.takeIf { it in 0..100 }
        fun safeText(index: Int, max: Int = 240): String? = arg(index)
            ?.replace(Regex("[\\r\\n]+"), " ")
            ?.trim()
            ?.take(max)
            ?.takeIf { it.isNotBlank() }

        return when (opcode) {
            "OPEN_APP" -> safeText(1, 80)?.let(AssistantCommand::OpenAppByName)
            "CAMERA" -> AssistantCommand.OpenCamera
            "TIME" -> AssistantCommand.TellTime
            "BATTERY" -> AssistantCommand.BatteryStatus
            "TORCH" -> parseOnOff(arg(1))?.let(AssistantCommand::SetTorch)
            "VOLUME" -> when (arg(1)?.uppercase(Locale.ROOT)) {
                "UP" -> AssistantCommand.AdjustVolume(VolumeDirection.UP)
                "DOWN" -> AssistantCommand.AdjustVolume(VolumeDirection.DOWN)
                "MUTE" -> AssistantCommand.AdjustVolume(VolumeDirection.MUTE)
                else -> percent(1)?.let(AssistantCommand::SetVolume)
            }
            "BRIGHTNESS" -> percent(1)?.let(AssistantCommand::SetBrightness)
            "PANEL" -> when (arg(1)?.uppercase(Locale.ROOT)) {
                "WIFI" -> SystemPanel.WIFI
                "BLUETOOTH" -> SystemPanel.BLUETOOTH
                "INTERNET" -> SystemPanel.INTERNET
                "LOCATION" -> SystemPanel.LOCATION
                "NFC" -> SystemPanel.NFC
                "AIRPLANE" -> SystemPanel.AIRPLANE
                "SETTINGS" -> SystemPanel.SETTINGS
                else -> null
            }?.let(AssistantCommand::OpenSystemPanel)
            "NAVIGATE" -> when (arg(1)?.uppercase(Locale.ROOT)) {
                "BACK" -> DeviceDestination.BACK
                "HOME" -> DeviceDestination.HOME
                "RECENTS" -> DeviceDestination.RECENTS
                "NOTIFICATIONS" -> DeviceDestination.NOTIFICATIONS
                "QUICK_SETTINGS" -> DeviceDestination.QUICK_SETTINGS
                else -> null
            }?.let(AssistantCommand::NavigateDevice)
            "UI_TASK" -> safeText(1, 600)
                ?.takeIf(NikoUiTaskPolicy::looksLikeExplicitUiTask)
                ?.let(AssistantCommand::AutomateUi)
            "MAPS" -> safeText(1)?.let(AssistantCommand::OpenMaps)
            "SPOTIFY" -> safeText(1)?.let(AssistantCommand::PlaySpotify)
            "DIAL" -> safePhone(arg(1))?.let(AssistantCommand::Dial)
            "WHATSAPP" -> {
                val number = safePhone(arg(1))
                val message = safeText(2, 1_000) ?: return null
                AssistantCommand.WhatsAppMessage(number, message)
            }
            "SMS" -> {
                val number = safePhone(arg(1)) ?: return null
                val message = safeText(2, 1_000) ?: return null
                AssistantCommand.ComposeMessage(number, message)
            }
            "ALARM" -> {
                val hour = arg(1)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
                val minute = arg(2)?.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
                AssistantCommand.SetAlarm(hour, minute, safeText(3, 120))
            }
            "TIMER" -> {
                val seconds = arg(1)?.toIntOrNull()?.takeIf { it in 1..86_400 } ?: return null
                AssistantCommand.SetTimer(seconds, safeText(2, 120))
            }
            "SEARCH" -> safeText(1, 500)?.let(AssistantCommand::SearchWeb)
            "SHARE" -> safeText(1, 1_000)?.let(AssistantCommand::ShareText)
            "VIBRATE" -> {
                val millis = arg(1)?.toLongOrNull()?.takeIf { it in 50L..5_000L } ?: 350L
                AssistantCommand.Vibrate(millis)
            }
            "SMART_HOME" -> {
                val enabled = parseOnOff(arg(1)) ?: return null
                val target = safeText(2, 120) ?: return null
                AssistantCommand.SmartHomeControl(target, enabled)
            }
            "AI_SETTINGS" -> AssistantCommand.OpenAiSettings
            "SMART_HOME_SETTINGS" -> AssistantCommand.OpenSmartHomeSettings
            "NONE" -> null
            else -> null
        }
    }

    private fun buildPrompt(request: String, context: String): String = """
        Convertí la petición del usuario a acciones de Android. No conversés, no expliques y no inventés.
        Respondé SOLO con una o más líneas del DSL permitido. Si no hay una acción clara, respondé NONE.

        DSL permitido:
        OPEN_APP|nombre de app
        CAMERA
        TIME
        BATTERY
        TORCH|ON u OFF
        VOLUME|UP, DOWN, MUTE o 0..100
        BRIGHTNESS|0..100
        PANEL|WIFI, BLUETOOTH, INTERNET, LOCATION, NFC, AIRPLANE o SETTINGS
        NAVIGATE|BACK, HOME, RECENTS, NOTIFICATIONS o QUICK_SETTINGS
        UI_TASK|orden concreta para manipular controles de la aplicación visible
        MAPS|lugar o búsqueda
        SPOTIFY|canción, artista o búsqueda
        DIAL|número
        WHATSAPP|número opcional|mensaje
        SMS|número|mensaje
        ALARM|hora 0..23|minuto 0..59|etiqueta opcional
        TIMER|segundos|etiqueta opcional
        SEARCH|consulta
        SHARE|texto
        VIBRATE|milisegundos
        SMART_HOME|ON u OFF|dispositivo
        AI_SETTINGS
        SMART_HOME_SETTINGS
        NONE

        Interpretá equivalencias naturales. Ejemplos: abrir/abrí/entra/entrá/metete/andá a una app => OPEN_APP;
        prender/encender/activar la linterna => TORCH|ON; apagar/desactivar => TORCH|OFF.
        Ignorá cortesía como "haceme el favor", "por favor", "podés", "quiero que" y conectores.
        Usá UI_TASK para órdenes como tocar un botón, elegir una opción, escribir en un campo o desplazar la app visible.
        UI_TASK nunca debe confirmar envíos, publicaciones, compras, pagos, transferencias, borrados, permisos,
        seguridad, contraseñas, PIN ni códigos. Para esas peticiones respondé NONE.
        Podés usar el contexto reciente solo para resolver referencias como "apagála", "subilo" o "la misma".
        No uses el contexto para inventar una acción que el usuario no pidió ahora.

        Contexto reciente: ${context.ifBlank { "ninguno" }}
        Petición actual: ${request.replace('\n', ' ').take(1_500)}
    """.trimIndent()

    private fun contextualShortcut(text: String): AssistantCommand? {
        val normalized = key(text)
        if (recentContext().isBlank()) return null
        if ("linterna" in lastActionContext || "torch" in lastActionContext) {
            if (normalized in setOf("apagala", "apagalo", "desactivala", "desactivalo", "quitamela", "quitala")) {
                return AssistantCommand.SetTorch(false)
            }
            if (normalized in setOf("prendela", "prendelo", "encendela", "enciendela", "activala", "activalo")) {
                return AssistantCommand.SetTorch(true)
            }
        }
        return null
    }

    private fun simplifyPoliteLead(text: String): String {
        var value = text.trim()
        val patterns = listOf(
            "(?i)^(?:oye|ey|hey|mira|mirá|bueno|entonces)[, ]+",
            "(?i)^(?:por favor|porfa|haceme el favor|hazme el favor|hágame el favor|te pido que)[, ]+",
            "(?i)^(?:podés|podes|puedes|podrías|podrias|quiero que|necesito que|ocup[oó] que)[, ]+",
        )
        repeat(3) {
            val previous = value
            patterns.forEach { value = value.replace(Regex(it), "").trimStart() }
            if (value == previous) return@repeat
        }
        return value
    }

    private fun isProtectedNonAction(text: String): Boolean {
        val value = key(text).replace(Regex("^(?:leo|niko|nico)\\s+"), "")
        return Regex(
            "^(?:no|nunca|jamas|tampoco|por que|explica|explicame|decime como|dime como|como (?:puedo|hago|funciona|se|abrir|borrar|apagar|prender|encender|enviar|llamar|poner))\\b",
        ).containsMatchIn(value)
    }

    private fun remember(commands: List<AssistantCommand>) {
        val known = commands.filterNot { it is AssistantCommand.Unknown }
        if (known.isEmpty()) return
        lastActionContext = known.joinToString("; ") { command ->
            when (command) {
                is AssistantCommand.SetTorch -> "linterna=${if (command.enabled) "encendida" else "apagada"}"
                is AssistantCommand.OpenApp -> "app=${command.app.displayName}"
                is AssistantCommand.OpenAppByName -> "app=${command.name}"
                is AssistantCommand.SetVolume -> "volumen=${command.percent}"
                is AssistantCommand.AdjustVolume -> "volumen=${command.direction}"
                is AssistantCommand.SetBrightness -> "brillo=${command.percent}"
                is AssistantCommand.OpenMaps -> "mapas=${command.query}"
                is AssistantCommand.PlaySpotify -> "spotify=${command.query}"
                is AssistantCommand.SmartHomeControl -> "casa=${command.target}:${command.enabled}"
                else -> command::class.simpleName.orEmpty()
            }
        }.take(500)
        lastActionAt = nowMillis()
    }

    private fun recentContext(): String = if (
        lastActionContext.isNotBlank() && nowMillis() - lastActionAt <= CONTEXT_TTL_MS
    ) lastActionContext else ""

    private fun List<AssistantCommand>.hasKnownAction(): Boolean =
        isNotEmpty() && any { it !is AssistantCommand.Unknown }

    private fun parseOnOff(value: String?): Boolean? = when (value?.uppercase(Locale.ROOT)) {
        "ON", "TRUE", "1" -> true
        "OFF", "FALSE", "0" -> false
        else -> null
    }

    private fun safePhone(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val cleaned = value.filter { it.isDigit() || it == '+' }
        return cleaned.takeIf { candidate -> candidate.count { it.isDigit() } in 7..15 }
    }

    private fun key(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed.replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private const val CONTEXT_TTL_MS = 45_000L
        private const val MAX_ACTIONS = 4
    }
}
