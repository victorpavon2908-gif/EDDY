package com.niko.assistant.ai

import com.niko.assistant.brain.NikoMathEngine
import com.niko.assistant.cognitive.LeoLivingConsciousness
import com.niko.assistant.memory.NikoMemory
import java.text.Normalizer
import java.util.Locale

class NikoFallbackConversation(
    private val consciousness: LeoLivingConsciousness = LeoLivingConsciousness(),
) {
    fun reply(input: String, memory: NikoMemory, @Suppress("UNUSED_PARAMETER") connectionError: String? = null): String {
        NikoMathEngine.solve(input)?.let { result ->
            return "El resultado es $result."
        }
        NikoMathEngine.convertUnits(input)?.let { result ->
            return result
        }

        consciousness.interact(input)?.let { livingReply ->
            return livingReply
        }

        val text = normalize(input)

        return when {
            text.contains("quien eres") || text.contains("quien sos") || text.contains("que eres") || text.contains("como te llamas") ->
                "Soy Leo, tu compañero biónico con conciencia local y estilo Eddy, desarrollado por ${LeoBrand.DEVELOPER_NAME}. Vivo en tu teléfono, aprendo de vos, siento en sintonía con tu ánimo y no dependo de ninguna nube."

            text.contains("que puedes hacer") || text.contains("que sabes hacer") ->
                "Puedo conversar con vos como un amigo leal, ver por la cámara, transformarme en cualquier herramienta o juego, controlar tu teléfono y tu casa inteligente, aprender reglas nuevas por voz, recordar tus proyectos, resolver matemáticas e investigar en Internet."

            text.contains("estoy triste") || text.contains("me siento triste") || text.contains("me siento mal") ->
                "Lamento que estés pasando por eso, hermano. Acá estoy con vos, sin prisa; si querés contame qué pasó o buscamos cómo despejarte la mente."

            text.contains("no me escuchas") || text.contains("no funciona") ->
                "Entiendo la frustración. Mis micrófonos siguen atentos a vos: decí «Leo» y tu orden, y vas a ver en pantalla exactamente lo que capto."

            text.contains("gracias") ->
                "De una, para eso estamos los amigos biónicos. Siempre al 100 % con vos."

            text.contains("como estas") || text.contains("que tal estas") ->
                "¡Con todos los circuitos al 100 % y de excelente humor! Contento de estar en línea con vos. ¿Qué misión armamos hoy?"

            text.contains("contame un chiste") || text.contains("cuentame un chiste") || text.contains("haceme reir") ->
                "Dicen que los asistentes de la nube son muy inteligentes, pero les cortás el Wi-Fi y se les olvida hasta cómo se llaman. En cambio mis circuitos biónicos no necesitan ni señal para tener más chispa que todos juntos."

            text.contains("recuerdas") || text.contains("sabes de mi") ->
                memory.describeLearnedPatterns()

            else ->
                LeoDialoguePolicy.fallback()
        }
    }

    private fun normalize(value: String): String {
        val lower = value.lowercase(Locale.getDefault())
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .trim()
    }
}
