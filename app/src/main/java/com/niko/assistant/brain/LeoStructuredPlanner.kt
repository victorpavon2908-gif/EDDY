package com.niko.assistant.brain

import com.niko.assistant.devicecontrol.NikoUiTaskPolicy
import com.niko.assistant.devicecontrol.NikoVisualContext
import java.text.Normalizer
import java.util.Locale

/** Origin of a structured action decision. */
enum class LeoPlanSource { LOCAL, LOCAL_SIMPLIFIED, CONTEXT, GROQ, FALLBACK }

/** Coarse safety level used before any command can reach ActionExecutor. */
enum class LeoPlanRisk { NONE, LOW, MEDIUM, HIGH }

data class LeoPlannerInput(
    val utterance: String,
    val memoryContext: String = "",
    val recentContext: String = "",
    val capabilities: Set<String> = LeoStructuredPlanner.DEFAULT_CAPABILITIES,
)

data class LeoPlanDecision(
    val commands: List<AssistantCommand>,
    val source: LeoPlanSource,
    val confidence: Double,
    val risk: LeoPlanRisk,
    val requiresConfirmation: Boolean,
    val reason: String? = null,
)

/**
 * LEO 0.12 semantic action planner.
 *
 * LocalBrain is always the first route. Cloud inference is only a semantic compiler:
 * it receives a constrained prompt and can return only the allow-listed DSL. Every line,
 * argument, range, arity and safety rule is validated locally before a command is exposed.
 */
class LeoStructuredPlanner(
    private val brain: LocalBrain,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val structuredCompletion: suspend (String) -> String?,
) {
    private var lastActionContext: String = ""
    private var lastActionAt: Long = 0L

    suspend fun resolveMany(text: String): List<AssistantCommand> = plan(text).commands

    suspend fun plan(text: String): LeoPlanDecision = plan(
        LeoPlannerInput(
            utterance = text,
            recentContext = recentContext(),
        ),
    )

    suspend fun plan(input: LeoPlannerInput): LeoPlanDecision {
        val original = input.utterance.trim()
        if (original.isBlank()) return fallback(input.utterance, "petición vacía")

        val deterministic = normalizeWhatsAppFollowUp(brain.understandMany(original), original, input.recentContext)
        if (deterministic.hasKnownAction()) {
            remember(deterministic)
            return accepted(deterministic, LeoPlanSource.LOCAL, 0.99)
        }

        if (NikoVisualContext.wantsScreenContext(original)) return fallback(original, "consulta visual")

        if (isProtectedNonAction(original) || isObviousInformationQuestion(original)) {
            return fallback(original, "pregunta o negación protegida")
        }

        val simplified = simplifyPoliteLead(original)
        if (simplified != original) {
            val retried = normalizeWhatsAppFollowUp(brain.understandMany(simplified), simplified, input.recentContext)
            if (retried.hasKnownAction()) {
                remember(retried)
                return accepted(retried, LeoPlanSource.LOCAL_SIMPLIFIED, 0.98)
            }
        }

        contextualShortcut(original, input.recentContext)?.let { command ->
            val commands = listOf(command)
            remember(commands)
            return accepted(commands, LeoPlanSource.CONTEXT, 0.95)
        }

        if (!looksLikeActionRequest(original)) return fallback(original, "sin intención de acción")

        val prompt = buildPrompt(input.copy(recentContext = input.recentContext.ifBlank { recentContext() }))
        val raw = runCatching { structuredCompletion(prompt) }.getOrNull()
            ?: return fallback(original, "Groq no disponible")

        val semantic = parseDsl(raw)
        if (semantic.isEmpty()) return fallback(original, "plan semántico vacío o inválido")
        val enabledCapabilities = input.capabilities.intersect(DEFAULT_CAPABILITIES)
        if (semantic.any { capabilityFor(it) !in enabledCapabilities }) {
            return fallback(original, "el plan pidió una capacidad no habilitada")
        }

        val risk = classifyRisk(semantic)
        val confidence = semanticConfidence(original, semantic)
        val needsConfirmation = needsConfirmation(risk, confidence, original)
        if (needsConfirmation) {
            return LeoPlanDecision(
                commands = listOf(AssistantCommand.Unknown(original)),
                source = LeoPlanSource.GROQ,
                confidence = confidence,
                risk = risk,
                requiresConfirmation = true,
                reason = "plan semántico sensible o ambiguo",
            )
        }

        remember(semantic)
        return LeoPlanDecision(
            commands = semantic,
            source = LeoPlanSource.GROQ,
            confidence = confidence,
            risk = risk,
            requiresConfirmation = false,
        )
    }

    /** Strict parser: one bad line invalidates the complete model plan. */
    fun parseDsl(value: String): List<AssistantCommand> {
        val body = stripFence(value) ?: return emptyList()
        val lines = body.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        if (lines.isEmpty() || lines.size > MAX_ACTIONS) return emptyList()
        if (lines.size == 1 && lines.single().equals("NONE", ignoreCase = true)) return emptyList()
        if (lines.any { it.substringBefore('|').trim().equals("NONE", ignoreCase = true) }) return emptyList()

        val parsed = lines.map(::parseLine)
        if (parsed.any { it == null }) return emptyList()
        return parsed.filterNotNull()
    }

    private fun parseLine(line: String): AssistantCommand? {
        if (line.length > MAX_DSL_LINE_LENGTH || line.any { it == '\u0000' || it.isISOControl() && it != '\t' }) return null
        val parts = line.split('|').map(String::trim)
        val opcode = parts.firstOrNull()?.uppercase(Locale.ROOT) ?: return null
        fun arity(vararg counts: Int): Boolean = parts.size in counts
        fun arg(index: Int): String? = parts.getOrNull(index)?.takeIf { it.isNotBlank() }
        fun percent(index: Int): Int? = arg(index)?.toIntOrNull()?.takeIf { it in 0..100 }
        fun safeText(index: Int, max: Int = 240): String? = arg(index)
            ?.replace(Regex("[\\r\\n]+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= max && it.none(Char::isISOControl) }

        return when (opcode) {
            "OPEN_APP" -> if (arity(2)) safeText(1, 80)?.let(AssistantCommand::OpenAppByName) else null
            "CAMERA" -> if (arity(1)) AssistantCommand.OpenCamera else null
            "TIME" -> if (arity(1)) AssistantCommand.TellTime else null
            "BATTERY" -> if (arity(1)) AssistantCommand.BatteryStatus else null
            "TORCH" -> if (arity(2)) parseOnOff(arg(1))?.let(AssistantCommand::SetTorch) else null
            "VOLUME" -> if (!arity(2)) null else when (arg(1)?.uppercase(Locale.ROOT)) {
                "UP" -> AssistantCommand.AdjustVolume(VolumeDirection.UP)
                "DOWN" -> AssistantCommand.AdjustVolume(VolumeDirection.DOWN)
                "MUTE" -> AssistantCommand.AdjustVolume(VolumeDirection.MUTE)
                else -> percent(1)?.let(AssistantCommand::SetVolume)
            }
            "BRIGHTNESS" -> if (arity(2)) percent(1)?.let(AssistantCommand::SetBrightness) else null
            "PANEL" -> if (!arity(2)) null else when (arg(1)?.uppercase(Locale.ROOT)) {
                "WIFI" -> SystemPanel.WIFI
                "BLUETOOTH" -> SystemPanel.BLUETOOTH
                "INTERNET" -> SystemPanel.INTERNET
                "LOCATION" -> SystemPanel.LOCATION
                "NFC" -> SystemPanel.NFC
                "AIRPLANE" -> SystemPanel.AIRPLANE
                "SETTINGS" -> SystemPanel.SETTINGS
                else -> null
            }?.let(AssistantCommand::OpenSystemPanel)
            "NAVIGATE" -> if (!arity(2)) null else when (arg(1)?.uppercase(Locale.ROOT)) {
                "BACK" -> DeviceDestination.BACK
                "HOME" -> DeviceDestination.HOME
                "RECENTS" -> DeviceDestination.RECENTS
                "NOTIFICATIONS" -> DeviceDestination.NOTIFICATIONS
                "QUICK_SETTINGS" -> DeviceDestination.QUICK_SETTINGS
                else -> null
            }?.let(AssistantCommand::NavigateDevice)
            "UI_TASK" -> if (!arity(2)) null else safeText(1, 600)
                ?.takeIf(NikoUiTaskPolicy::looksLikeExplicitUiTask)
                ?.let(AssistantCommand::AutomateUi)
            "MAPS" -> if (arity(2)) safeText(1, 240)?.let(AssistantCommand::OpenMaps) else null
            "SPOTIFY" -> if (arity(2)) safeText(1, 240)?.let(AssistantCommand::PlaySpotify) else null
            "DIAL" -> if (arity(2)) safePhone(arg(1))?.let(AssistantCommand::Dial) else null
            "WHATSAPP" -> {
                if (!arity(3)) return null
                val rawNumber = parts[1]
                val number = if (rawNumber.isBlank()) null else safePhone(rawNumber) ?: return null
                val message = safeText(2, 1_000) ?: return null
                AssistantCommand.WhatsAppMessage(number, message)
            }
            "SMS" -> {
                if (!arity(3)) return null
                val number = safePhone(arg(1)) ?: return null
                val message = safeText(2, 1_000) ?: return null
                AssistantCommand.ComposeMessage(number, message)
            }
            "ALARM" -> {
                if (!arity(3, 4)) return null
                val hour = arg(1)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
                val minute = arg(2)?.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
                AssistantCommand.SetAlarm(hour, minute, if (parts.size == 4) safeText(3, 120) else null)
            }
            "TIMER" -> {
                if (!arity(2, 3)) return null
                val seconds = arg(1)?.toIntOrNull()?.takeIf { it in 1..86_400 } ?: return null
                AssistantCommand.SetTimer(seconds, if (parts.size == 3) safeText(2, 120) else null)
            }
            "SEARCH" -> if (arity(2)) safeText(1, 500)?.let(AssistantCommand::SearchWeb) else null
            "SHARE" -> if (arity(2)) safeText(1, 1_000)?.let(AssistantCommand::ShareText) else null
            "VIBRATE" -> {
                if (!arity(2)) return null
                val millis = arg(1)?.toLongOrNull()?.takeIf { it in 50L..5_000L } ?: return null
                AssistantCommand.Vibrate(millis)
            }
            "SMART_HOME" -> {
                if (!arity(3)) return null
                val enabled = parseOnOff(arg(1)) ?: return null
                val target = safeText(2, 120) ?: return null
                AssistantCommand.SmartHomeControl(target, enabled)
            }
            "AI_SETTINGS" -> if (arity(1)) AssistantCommand.OpenAiSettings else null
            "SMART_HOME_SETTINGS" -> if (arity(1)) AssistantCommand.OpenSmartHomeSettings else null
            "NONE" -> null
            else -> null
        }
    }

    private fun buildPrompt(input: LeoPlannerInput): String {
        val capabilities = input.capabilities.intersect(DEFAULT_CAPABILITIES).sorted().joinToString(", ")
        return """
            Sos el compilador de acciones de LEO para Android. No conversés, no expliques, no uses Markdown y no inventés.
            Respondé SOLO con una o más líneas del DSL permitido. Si no hay una acción clara, respondé exactamente NONE.

            Capacidades habilitadas: ${capabilities.ifBlank { "ninguna" }}
            No uses ningún opcode que no aparezca en Capacidades habilitadas.

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
            VIBRATE|milisegundos 50..5000
            SMART_HOME|ON u OFF|dispositivo
            AI_SETTINGS
            SMART_HOME_SETTINGS
            NONE

            Entendé español natural de Nicaragua: abrime, metete, andá, buscame, poneme, prendeme, apagála, subime,
            bajame, mandale, decile, haceme el favor, porfa, podés, fijate, mae y muletillas equivalentes.
            No cambies el contenido literal de mensajes dictados por el usuario.
            UI_TASK nunca puede confirmar envíos, publicaciones, compras, pagos, transferencias, borrados, permisos,
            seguridad, contraseñas, PIN ni códigos. Para eso respondé NONE.
            Una pregunta, una negación o una frase ambigua debe ser NONE.
            Máximo $MAX_ACTIONS acciones y siempre en el orden pedido.

            Memoria relevante: ${sanitizeContext(input.memoryContext)}
            Contexto reciente: ${sanitizeContext(input.recentContext)}
            Petición actual: ${input.utterance.replace('\n', ' ').take(1_500)}
        """.trimIndent()
    }

    private fun sanitizeContext(value: String): String = value.replace(Regex("[\\r\\n]+"), " ").trim().take(700).ifBlank { "ninguno" }

    private fun normalizeWhatsAppFollowUp(
        commands: List<AssistantCommand>,
        original: String,
        context: String,
    ): List<AssistantCommand> = commands.map { command ->
        if (command is AssistantCommand.ComposeMessage &&
            !Regex("\\b(?:sms|mensajes de texto)\\b").containsMatchIn(key(original)) &&
            context.contains("whatsapp", ignoreCase = true)
        ) AssistantCommand.WhatsAppMessage(command.number.takeIf(String::isNotBlank), command.message) else command
    }

    private fun contextualShortcut(text: String, context: String): AssistantCommand? {
        val normalized = key(text)
        if (context.isBlank()) return null
        if (context.contains("linterna", true) || context.contains("torch", true)) {
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
            "(?i)^(?:leo|niko|nico)[, ]+",
            "(?i)^(?:oye|ey|hey|mira|mirá|fijate|fíjate|mae|bueno|entonces|este|eh|mmm)[, ]+",
            "(?i)^(?:por favor|porfa|haceme el favor(?: de)?|hazme el favor(?: de)?|hágame el favor(?: de)?|te pido que)[, ]+",
            "(?i)^(?:podés|podes|puedes|podrías|podrias|quiero que|necesito que|ocup[oó] que)[, ]+",
        )
        repeat(6) {
            val previous = value
            patterns.forEach { value = value.replace(Regex(it), "").trimStart() }
            if (value == previous) return value
        }
        return value
    }

    private fun isProtectedNonAction(text: String): Boolean {
        val value = key(text).replace(Regex("^(?:leo|niko|nico)\\s+"), "")
        return Regex(
            "^(?:no|nunca|jamas|tampoco|por que|explica|explicame|decime como|dime como|como (?:puedo|hago|funciona|se|abrir|borrar|apagar|prender|encender|enviar|llamar|poner))\\b",
        ).containsMatchIn(value)
    }

    private fun isObviousInformationQuestion(text: String): Boolean {
        val value = key(text).replace(Regex("^(?:leo|niko|nico)\\s+"), "")
        return Regex("^(?:quien|quienes|que es|que son|cuando|donde|por que|cual|cuales|cuanto|cuantos|contame|hablame de)\\b")
            .containsMatchIn(value)
    }

    private fun looksLikeActionRequest(text: String): Boolean {
        val value = key(text).replace(Regex("^(?:leo|niko|nico)\\s+"), "")
        return ACTION_CUE.containsMatchIn(value) ||
            NikoUiTaskPolicy.looksLikeExplicitUiTask(text) ||
            Regex("\\b(?:linterna|volumen|brillo|wifi|bluetooth|alarma|temporizador|whatsapp|spotify|camara|ajustes|notificaciones)\\b")
                .containsMatchIn(value)
    }

    private fun capabilityFor(command: AssistantCommand): String = when (command) {
        is AssistantCommand.OpenApp, is AssistantCommand.OpenAppByName -> "OPEN_APP"
        AssistantCommand.OpenCamera -> "CAMERA"
        AssistantCommand.TellTime -> "TIME"
        AssistantCommand.BatteryStatus -> "BATTERY"
        is AssistantCommand.SetTorch -> "TORCH"
        is AssistantCommand.SetVolume, is AssistantCommand.AdjustVolume -> "VOLUME"
        is AssistantCommand.SetBrightness -> "BRIGHTNESS"
        is AssistantCommand.OpenSystemPanel -> "PANEL"
        is AssistantCommand.NavigateDevice -> "NAVIGATE"
        is AssistantCommand.AutomateUi -> "UI_TASK"
        is AssistantCommand.OpenMaps -> "MAPS"
        is AssistantCommand.PlaySpotify -> "SPOTIFY"
        is AssistantCommand.Dial -> "DIAL"
        is AssistantCommand.WhatsAppMessage -> "WHATSAPP"
        is AssistantCommand.ComposeMessage -> "SMS"
        is AssistantCommand.SetAlarm -> "ALARM"
        is AssistantCommand.SetTimer -> "TIMER"
        is AssistantCommand.SearchWeb -> "SEARCH"
        is AssistantCommand.ShareText -> "SHARE"
        is AssistantCommand.Vibrate -> "VIBRATE"
        is AssistantCommand.SmartHomeControl -> "SMART_HOME"
        AssistantCommand.OpenAiSettings -> "AI_SETTINGS"
        AssistantCommand.OpenSmartHomeSettings -> "SMART_HOME_SETTINGS"
        AssistantCommand.Greeting, AssistantCommand.MemorySummary, AssistantCommand.ClearMemory, is AssistantCommand.Unknown -> "LOCAL_ONLY"
    }

    private fun semanticConfidence(text: String, commands: List<AssistantCommand>): Double {
        val normalized = key(text)
        var confidence = if (ACTION_CUE.containsMatchIn(normalized)) 0.90 else 0.82
        if (commands.size > 2) confidence -= 0.03
        if (Regex("\\b(?:eso|esa|ese|aquello|lo mismo|como antes)\\b").containsMatchIn(normalized)) confidence -= 0.08
        if (normalized.length < 6) confidence -= 0.08
        return confidence.coerceIn(0.0, 0.99)
    }

    private fun classifyRisk(commands: List<AssistantCommand>): LeoPlanRisk = commands.maxOfOrNull { command ->
        when (command) {
            is AssistantCommand.SmartHomeControl -> LeoPlanRisk.HIGH
            is AssistantCommand.AutomateUi,
            is AssistantCommand.Dial,
            is AssistantCommand.ComposeMessage,
            is AssistantCommand.WhatsAppMessage,
            is AssistantCommand.ShareText -> LeoPlanRisk.MEDIUM
            is AssistantCommand.SetAlarm,
            is AssistantCommand.SetTimer,
            is AssistantCommand.SetTorch,
            is AssistantCommand.SetVolume,
            is AssistantCommand.AdjustVolume,
            is AssistantCommand.SetBrightness -> LeoPlanRisk.LOW
            else -> LeoPlanRisk.NONE
        }
    } ?: LeoPlanRisk.NONE

    private fun needsConfirmation(risk: LeoPlanRisk, confidence: Double, text: String): Boolean {
        if (risk == LeoPlanRisk.HIGH) return true
        if (risk == LeoPlanRisk.MEDIUM && confidence < 0.84) return true
        return confidence < 0.72 || Regex("\\b(?:creo que|tal vez|quizas|quiza|no se si)\\b").containsMatchIn(key(text))
    }

    private fun accepted(commands: List<AssistantCommand>, source: LeoPlanSource, confidence: Double): LeoPlanDecision {
        val risk = classifyRisk(commands)
        return LeoPlanDecision(commands, source, confidence, risk, false)
    }

    private fun fallback(original: String, reason: String): LeoPlanDecision = LeoPlanDecision(
        commands = listOf(AssistantCommand.Unknown(original)),
        source = LeoPlanSource.FALLBACK,
        confidence = 0.0,
        risk = LeoPlanRisk.NONE,
        requiresConfirmation = false,
        reason = reason,
    )

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

    private fun List<AssistantCommand>.hasKnownAction(): Boolean = isNotEmpty() && any { it !is AssistantCommand.Unknown }

    private fun parseOnOff(value: String?): Boolean? = when (value?.uppercase(Locale.ROOT)) {
        "ON", "TRUE", "1" -> true
        "OFF", "FALSE", "0" -> false
        else -> null
    }

    private fun safePhone(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (!Regex("^\\+?[0-9 ()-]{7,24}$").matches(value)) return null
        val cleaned = value.filter { it.isDigit() || it == '+' }
        if (cleaned.count { it == '+' } > 1 || '+' in cleaned.drop(1)) return null
        return cleaned.takeIf { candidate -> candidate.count(Char::isDigit) in 7..15 }
    }

    private fun stripFence(value: String): String? {
        val trimmed = value.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val lines = trimmed.lines()
        if (lines.size < 3 || lines.last().trim() != "```") return null
        return lines.drop(1).dropLast(1).joinToString("\n").trim()
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
        private const val MAX_DSL_LINE_LENGTH = 1_200

        val DEFAULT_CAPABILITIES: Set<String> = setOf(
            "OPEN_APP", "CAMERA", "TIME", "BATTERY", "TORCH", "VOLUME", "BRIGHTNESS", "PANEL",
            "NAVIGATE", "UI_TASK", "MAPS", "SPOTIFY", "DIAL", "WHATSAPP", "SMS", "ALARM", "TIMER",
            "SEARCH", "SHARE", "VIBRATE", "SMART_HOME", "AI_SETTINGS", "SMART_HOME_SETTINGS",
        )

        private val ACTION_CUE = Regex(
            "\\b(?:abre|abri|abrime|abreme|abrirme|abrir|entra|entrar|entremos|metete|meterte|and[aá]|ve|prende|prendeme|prenderme|enciende|encende|encenderme|apaga|apagame|apagarme|activa|activar|desactiva|desactivar|sube|subime|baja|bajame|pon|poneme|reproduce|toca|dale|pulsa|presiona|escribe|escribi|manda|mandale|envia|enviale|llama|marca|busca|buscame|lleva|llevame|comparte|comparti|vibra|configura|mostra|muestra|regresa|volve|retrocede)\\b",
        )
    }
}
