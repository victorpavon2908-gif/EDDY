package com.eddy.assistant.brain

sealed interface AssistantCommand {
    data class OpenApp(val app: SupportedApp) : AssistantCommand
    data object OpenCamera : AssistantCommand
    data object TellTime : AssistantCommand
    data object Greeting : AssistantCommand
    data object MemorySummary : AssistantCommand
    data class Unknown(val originalText: String) : AssistantCommand
}

enum class SupportedApp(val displayName: String, val packageName: String) {
    YOUTUBE("YouTube", "com.google.android.youtube"),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    SPOTIFY("Spotify", "com.spotify.music"),
}
