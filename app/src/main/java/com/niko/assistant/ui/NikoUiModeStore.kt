package com.niko.assistant.ui

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context
import java.text.Normalizer
import java.util.Locale

enum class NikoUiMode(val id: String, val title: String) {
    ASSISTANT("assistant", "LEO"),
    VOICE_DIAGNOSTICS("voice_diagnostics", "Diagnóstico de voz"),
    TOOLBOX("toolbox", "Transformaciones"),
    CAMERA("camera", "Cámara"),
    VIDEO("video", "Grabadora de video"),
    AUDIO_RECORDER("audio_recorder", "Grabadora de audio"),
    MUSIC("music", "Música"),
    CALCULATOR("calculator", "Calculadora"),
    STOPWATCH("stopwatch", "Cronómetro"),
    TIMER("timer", "Temporizador"),
    CLOCK("clock", "Reloj"),
    NOTES("notes", "Notas"),
    CONVERTER("converter", "Conversor"),
}

object NikoUiModeStore {
    private const val PREFS = UpgradeIdentity.uiPreferences
    private const val KEY_MODE = "mode"

    fun read(context: Context): NikoUiMode {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, NikoUiMode.ASSISTANT.id)
        return NikoUiMode.entries.firstOrNull { it.id == id } ?: NikoUiMode.ASSISTANT
    }

    fun set(context: Context, mode: NikoUiMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.id).apply()
    }

    fun resolve(requestedName: String): NikoUiMode? {
        val text = normalize(requestedName)
        return when {
            text in setOf("leo", "niko", "inicio", "principal", "pantalla principal", "asistente") -> NikoUiMode.ASSISTANT
            (text.contains("diagnostico") || text.contains("prueba")) && (text.contains("voz") || text.contains("escucha") || text.contains("microfono") || text.contains("wake")) -> NikoUiMode.VOICE_DIAGNOSTICS
            text in setOf("transformaciones", "herramientas") -> NikoUiMode.TOOLBOX
            text in setOf("camara", "camera") -> NikoUiMode.CAMERA
            text in setOf("video", "grabadora de video") -> NikoUiMode.VIDEO
            text in setOf("grabadora", "grabadora de audio", "grabador de audio") -> NikoUiMode.AUDIO_RECORDER
            text in setOf("musica", "reproductor", "reproductor de musica") -> NikoUiMode.MUSIC
            text.contains("calculadora") || text == "calculator" -> NikoUiMode.CALCULATOR
            text.contains("cronometro") -> NikoUiMode.STOPWATCH
            text.contains("temporizador") || text.contains("cuenta regresiva") -> NikoUiMode.TIMER
            text == "reloj" || text.contains("hora mundial") -> NikoUiMode.CLOCK
            text.contains("nota") || text.contains("bloc") -> NikoUiMode.NOTES
            text.contains("conversor") || text.contains("convertidor") || text.contains("unidades") -> NikoUiMode.CONVERTER
            else -> null
        }
    }

    fun isReturnHomePhrase(raw: String): Boolean {
        val text = normalize(raw)
        return (text.contains("vuelve") || text.contains("regresa") || text.contains("volver") || text.contains("regresar")) && (text.contains("pantalla principal") || text.contains("inicio") || text.contains("leo") || text.contains("niko"))
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
