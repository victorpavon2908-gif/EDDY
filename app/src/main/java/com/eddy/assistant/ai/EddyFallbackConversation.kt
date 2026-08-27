package com.eddy.assistant.ai

import com.eddy.assistant.memory.EddyMemory
import java.text.Normalizer
import java.util.Locale

class EddyFallbackConversation {
    fun reply(input: String, memory: EddyMemory): String {
        val text = normalize(input)

        return when {
            text.contains("quien eres") || text.contains("que eres") ->
                "Soy EDDY, tu asistente personal. Puedo escucharte, recordar contexto local, abrir aplicaciones y ayudarte con acciones del teléfono."

            text.contains("que puedes hacer") || text.contains("que sabes hacer") ->
                "Puedo abrir aplicaciones, preparar llamadas y mensajes, crear alarmas, buscar lugares en mapas, recordar patrones y conversar contigo. Para respuestas más amplias, conecta mi backend de inteligencia artificial."

            text.contains("gracias") ->
                "Siempre. Estoy aquí cuando me necesites."

            text.contains("recuerdas") || text.contains("sabes de mi") ->
                memory.describeLearnedPatterns()

            else ->
                "Entendí lo que dijiste, pero mi motor de inteligencia artificial remoto todavía no está conectado. Aun así, lo guardé en mi contexto local y puedo seguir ejecutando mis funciones del teléfono."
        }
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.getDefault())
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }
}
