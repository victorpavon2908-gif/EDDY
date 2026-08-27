package com.eddy.assistant.brain

sealed interface AssistantCommand {
    data class OpenApp(val app: SupportedApp) : AssistantCommand
    data object OpenCamera : AssistantCommand
    data object TellTime : AssistantCommand
    data object Greeting : AssistantCommand
    data object MemorySummary : AssistantCommand
    data object ClearMemory : AssistantCommand
    data class Dial(val number: String) : AssistantCommand
    data class ComposeMessage(val number: String, val message: String) : AssistantCommand
    data class WhatsAppMessage(val number: String?, val message: String) : AssistantCommand
    data class PlaySpotify(val query: String) : AssistantCommand
    data class SetAlarm(val hour: Int, val minute: Int, val label: String?) : AssistantCommand
    data class SetTimer(val seconds: Int, val label: String?) : AssistantCommand
    data class OpenMaps(val query: String) : AssistantCommand
    data class SearchWeb(val query: String) : AssistantCommand
    data class ShareText(val text: String) : AssistantCommand
    data class SetTorch(val enabled: Boolean) : AssistantCommand
    data class SetVolume(val percent: Int) : AssistantCommand
    data class AdjustVolume(val direction: VolumeDirection) : AssistantCommand
    data class SetBrightness(val percent: Int) : AssistantCommand
    data class OpenSystemPanel(val panel: SystemPanel) : AssistantCommand
    data object BatteryStatus : AssistantCommand
    data class Vibrate(val milliseconds: Long = 350L) : AssistantCommand
    data class SmartHomeControl(
        val target: String,
        val enabled: Boolean,
    ) : AssistantCommand
    data object OpenSmartHomeSettings : AssistantCommand
    data class Unknown(val originalText: String) : AssistantCommand
}

enum class VolumeDirection {
    UP,
    DOWN,
    MUTE,
}

enum class SystemPanel {
    WIFI,
    BLUETOOTH,
    INTERNET,
    LOCATION,
    NFC,
    AIRPLANE,
    SETTINGS,
}

enum class SupportedApp(val displayName: String, val packageName: String) {
    YOUTUBE("YouTube", "com.google.android.youtube"),
    WHATSAPP("WhatsApp", "com.whatsapp"),
    SPOTIFY("Spotify", "com.spotify.music"),
    MAPS("Google Maps", "com.google.android.apps.maps"),
    CHROME("Chrome", "com.android.chrome"),
    GMAIL("Gmail", "com.google.android.gm"),
}
