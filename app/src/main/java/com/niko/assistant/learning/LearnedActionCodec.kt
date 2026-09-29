package com.niko.assistant.learning

import com.niko.assistant.brain.AssistantCommand

/** Serializes only repeatable, non-communicating actions accepted by the strict DSL parser. */
object LearnedActionCodec {
    fun encode(commands: List<AssistantCommand>): String? {
        if (commands.isEmpty() || commands.size > 4) return null
        val lines = commands.map { command -> encode(command) ?: return null }
        return lines.joinToString("\n")
    }

    private fun encode(command: AssistantCommand): String? = when (command) {
        is AssistantCommand.OpenApp -> safe("OPEN_APP", command.app.displayName, 80)
        is AssistantCommand.OpenAppByName -> safe("OPEN_APP", command.name, 80)
        AssistantCommand.OpenCamera -> "CAMERA"
        AssistantCommand.TellTime -> "TIME"
        AssistantCommand.BatteryStatus -> "BATTERY"
        is AssistantCommand.SetTorch -> "TORCH|${if (command.enabled) "ON" else "OFF"}"
        is AssistantCommand.SetVolume -> command.percent.takeIf { it in 0..100 }?.let { "VOLUME|$it" }
        is AssistantCommand.AdjustVolume -> "VOLUME|${command.direction.name}"
        is AssistantCommand.SetBrightness -> command.percent.takeIf { it in 0..100 }?.let { "BRIGHTNESS|$it" }
        is AssistantCommand.OpenSystemPanel -> "PANEL|${command.panel.name}"
        is AssistantCommand.NavigateDevice -> "NAVIGATE|${command.destination.name}"
        is AssistantCommand.OpenMaps -> safe("MAPS", command.query, 240)
        is AssistantCommand.PlaySpotify -> safe("SPOTIFY", command.query, 240)
        is AssistantCommand.SetAlarm -> listOfNotNull(
            "ALARM", command.hour.takeIf { it in 0..23 }?.toString(), command.minute.takeIf { it in 0..59 }?.toString(),
            command.label?.let { safeArgument(it, 120) },
        ).takeIf { it.size == if (command.label == null) 3 else 4 }?.joinToString("|")
        is AssistantCommand.SetTimer -> listOfNotNull(
            "TIMER", command.seconds.takeIf { it in 1..86_400 }?.toString(), command.label?.let { safeArgument(it, 120) },
        ).takeIf { it.size == if (command.label == null) 2 else 3 }?.joinToString("|")
        is AssistantCommand.SearchWeb -> safe("SEARCH", command.query, 500)
        is AssistantCommand.Vibrate -> command.milliseconds.takeIf { it in 50L..5_000L }?.let { "VIBRATE|$it" }
        AssistantCommand.OpenAiSettings -> "AI_SETTINGS"
        AssistantCommand.OpenSmartHomeSettings -> "SMART_HOME_SETTINGS"
        // Never replay communication, free-form UI automation, memory deletion or home control.
        is AssistantCommand.ComposeMessage,
        is AssistantCommand.WhatsAppMessage,
        is AssistantCommand.Dial,
        is AssistantCommand.ShareText,
        is AssistantCommand.AutomateUi,
        is AssistantCommand.SmartHomeControl,
        AssistantCommand.ClearMemory,
        AssistantCommand.Greeting,
        AssistantCommand.MemorySummary,
        is AssistantCommand.Unknown -> null
    }

    private fun safe(opcode: String, value: String, max: Int): String? = safeArgument(value, max)?.let { "$opcode|$it" }

    private fun safeArgument(value: String, max: Int): String? = value.trim()
        .takeIf { it.isNotBlank() && it.length <= max && '|' !in it && '\n' !in it && '\r' !in it && it.none(Char::isISOControl) }
}
