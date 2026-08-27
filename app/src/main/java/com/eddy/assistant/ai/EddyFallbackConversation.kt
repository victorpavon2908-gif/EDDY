package com.eddy.assistant.ai

import com.eddy.assistant.brain.EddyMathEngine
import com.eddy.assistant.memory.EddyMemory
import java.text.Normalizer
import java.util.Locale

class EddyFallbackConversation {
    fun reply(input: String, memory: EddyMemory): String {
        EddyMathEngine.solve(input)?.let { result ->
            return "El resultado es $result."
        }

        val text = normalize(input)

        return when {
            text.contains("quien eres") || text.contains("que eres") ->
                "Soy EDDY, tu asistente personal. Estoy hecho para ayudarte de una con el teléfono, tu casa inteligente y lo que vayás necesitando."

            text.contains("que puedes hacer") || text.contains("que sabes hacer") ->
                "Puedo abrir apps, preparar llamadas y WhatsApp, poner música en Spotify, usar linterna, volumen, brillo, alarmas, mapas, batería y ajustes del teléfono, controlar dispositivos de tu casa, resolver operaciones matemáticas y buscar información en Internet con mi backend web."

            text.contains("gracias") ->
                "De una. Aquí estoy para lo que ocupés."

            text.contains("como estas") ->
                "Todo tuani por aquí. Decime qué ocupás."

            text.contains("recuerdas") || text.contains("sabes de mi") ->
                memory.describeLearnedPatterns()

            else ->
                "Te entendí, pero todavía no tengo una acción local específica para eso. Si es una pregunta de información, EDDY intenta investigarla automáticamente cuando el backend está disponible."
        }
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.getDefault())
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }
}
