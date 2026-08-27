package com.eddy.assistant.memory

import android.content.Context
import com.eddy.assistant.brain.AssistantCommand
import com.eddy.assistant.brain.SupportedApp
import org.json.JSONArray

class EddyMemory(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("eddy_memory", Context.MODE_PRIVATE)

    fun rememberUtterance(text: String) {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return

        val current = readHistory().toMutableList()
        current.add(cleaned)
        while (current.size > MAX_HISTORY) current.removeAt(0)

        val array = JSONArray()
        current.forEach(array::put)
        prefs.edit()
            .putString(KEY_HISTORY, array.toString())
            .putInt(KEY_TOTAL_INTERACTIONS, prefs.getInt(KEY_TOTAL_INTERACTIONS, 0) + 1)
            .apply()
    }

    fun rememberCommand(command: AssistantCommand) {
        when (command) {
            is AssistantCommand.OpenApp -> increment("app_${command.app.name}")
            AssistantCommand.OpenCamera -> increment("camera")
            AssistantCommand.TellTime -> increment("time")
            AssistantCommand.Greeting -> increment("greeting")
            AssistantCommand.MemorySummary -> increment("memory_summary")
            is AssistantCommand.Unknown -> increment("unknown")
        }
    }

    fun describeLearnedPatterns(): String {
        val total = prefs.getInt(KEY_TOTAL_INTERACTIONS, 0)
        if (total == 0) {
            return "Todavía estoy empezando a conocerte. Mientras hables conmigo iré recordando tus solicitudes en este teléfono."
        }

        val favoriteApp = SupportedApp.entries
            .map { app -> app to prefs.getInt("app_${app.name}", 0) }
            .maxByOrNull { it.second }
            ?.takeIf { it.second > 0 }

        val cameraCount = prefs.getInt("camera", 0)
        val timeCount = prefs.getInt("time", 0)

        val strongest = buildList {
            favoriteApp?.let { add(it.second to "abrir ${it.first.displayName}") }
            if (cameraCount > 0) add(cameraCount to "usar la cámara")
            if (timeCount > 0) add(timeCount to "consultar la hora")
        }.maxByOrNull { it.first }

        return if (strongest != null) {
            "He registrado $total interacciones contigo. Hasta ahora, lo que más me pides es ${strongest.second}. Seguiré aprendiendo de lo que me solicites dentro de EDDY."
        } else {
            "He registrado $total interacciones contigo. Todavía necesito más uso para encontrar un patrón claro."
        }
    }

    private fun increment(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun readHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index -> array.optString(index) }
                .filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY_HISTORY = "history"
        private const val KEY_TOTAL_INTERACTIONS = "total_interactions"
        private const val MAX_HISTORY = 50
    }
}
