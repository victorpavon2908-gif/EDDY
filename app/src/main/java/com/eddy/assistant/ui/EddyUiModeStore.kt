package com.eddy.assistant.ui

import android.content.Context
import java.text.Normalizer
import java.util.Locale

enum class EddyUiMode(val id: String, val title: String) {
    ASSISTANT("assistant", "EDDY"),
    CALCULATOR("calculator", "Calculadora"),
    STOPWATCH("stopwatch", "Cronómetro"),
    TIMER("timer", "Temporizador"),
    CLOCK("clock", "Reloj"),
    NOTES("notes", "Notas"),
    CONVERTER("converter", "Conversor"),
}

object EddyUiModeStore {
    private const val PREFS = "eddy_ui_mode"
    private const val KEY_MODE = "mode"

    fun read(context: Context): EddyUiMode {
        val id = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, EddyUiMode.ASSISTANT.id)
        return EddyUiMode.entries.firstOrNull { it.id == id } ?: EddyUiMode.ASSISTANT
    }

    fun set(context: Context, mode: EddyUiMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, mode.id).apply()
    }

    fun resolve(requestedName: String): EddyUiMode? {
        val text = normalize(requestedName)
        return when {
            text in setOf("eddy", "inicio", "principal", "pantalla principal", "asistente") -> EddyUiMode.ASSISTANT
            text.contains("calculadora") || text == "calculator" -> EddyUiMode.CALCULATOR
            text.contains("cronometro") -> EddyUiMode.STOPWATCH
            text.contains("temporizador") || text.contains("cuenta regresiva") -> EddyUiMode.TIMER
            text == "reloj" || text.contains("hora mundial") -> EddyUiMode.CLOCK
            text.contains("nota") || text.contains("bloc") -> EddyUiMode.NOTES
            text.contains("conversor") || text.contains("convertidor") || text.contains("unidades") -> EddyUiMode.CONVERTER
            else -> null
        }
    }

    fun isReturnHomePhrase(raw: String): Boolean {
        val text = normalize(raw)
        return (text.contains("vuelve") || text.contains("regresa") || text.contains("volver") || text.contains("regresar")) &&
            (text.contains("pantalla principal") || text.contains("inicio") || text.contains("eddy"))
    }

    private fun normalize(value: String): String = Normalizer
        .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
