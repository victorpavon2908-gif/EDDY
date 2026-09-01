package com.niko.assistant.ai

import com.niko.assistant.brain.NikoMathEngine
import com.niko.assistant.memory.NikoMemory
import java.text.Normalizer
import java.util.Locale

class NikoFallbackConversation {
    fun reply(input: String, memory: NikoMemory, connectionError: String? = null): String {
        NikoMathEngine.solve(input)?.let { result ->
            return "El resultado es $result."
        }

        val text = normalize(input)

        return when {
            text.contains("quien eres") || text.contains("quien sos") || text.contains("que eres") || text.contains("como te llamas") ->
                "Soy NIKO, tu asistente personal. Estoy hecho para ayudarte de una con el teléfono, tu casa inteligente y lo que vayás necesitando."

            text.contains("que puedes hacer") || text.contains("que sabes hacer") ->
                "Puedo abrir apps, preparar llamadas y WhatsApp, poner música en Spotify, usar linterna, volumen, brillo, alarmas, mapas, batería y ajustes del teléfono, controlar dispositivos de tu casa, resolver operaciones matemáticas y conversar y consultar información con GroqCloud cuando hay Internet."

            text.contains("estoy triste") || text.contains("me siento triste") ->
                "Siento que estés pasando por eso. ¿Querés contarme qué pasó?"

            text.contains("no me escuchas") || text.contains("no funciona") ->
                "Entiendo la frustración. Decí NIKO y después qué hora es; la frase que escuche aparecerá en pantalla."

            text.contains("gracias") ->
                "De una. Aquí estoy para lo que ocupés."

            text.contains("como estas") ->
                "Todo tuani por aquí. Decime qué ocupás."

            text.contains("recuerdas") || text.contains("sabes de mi") ->
                memory.describeLearnedPatterns()

            else ->
                "${connectionError ?: "Configurá GroqCloud en Ajustes o instalá el modelo de conversación local para ampliar mis respuestas."} Puedo seguir ayudándote con órdenes del teléfono y tu memoria personal."
        }
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.getDefault())
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }
}
