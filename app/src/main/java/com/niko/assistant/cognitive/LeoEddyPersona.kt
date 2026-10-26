package com.niko.assistant.cognitive

import com.niko.assistant.cognitive.LeoEmotionClassifier.Emotion
import com.niko.assistant.memory.MemoryLearning

/**
 * Motor de Personalidad Dinámica «Eddy Lab Rats» y Prosodia Expresiva Local (100% soberano).
 *
 * Permite conmutar entre:
 * - [PersonaMode.EDDY_CLASSIC]: ingenioso, rápido, con chispa amistosa y lealtad total (respetando siempre
 *   la política de humor del usuario y desactivando bromas automáticamente si detecta tristeza o estrés).
 * - [PersonaMode.MISSION_CONTROL]: estilo táctico de centro de mando biónico, conciso y estructurado.
 * - [PersonaMode.EMPATHIC_FOCUS]: cálido, sereno y directo para estudio o apoyo emocional.
 */
class LeoEddyPersona(
    initialMode: PersonaMode = PersonaMode.EDDY_CLASSIC,
) {
    enum class PersonaMode(val label: String) {
        EDDY_CLASSIC("Modo Eddy (Ingenioso y Leal)"),
        MISSION_CONTROL("Modo Misión (Centro de Mando)"),
        EMPATHIC_FOCUS("Modo Enfoque Empático"),
    }

    data class ExpressiveProsody(
        val pitchMultiplier: Float,
        val rateMultiplier: Float,
        val emblemPulseHz: Float,
        val vocalToneTag: String,
    )

    @Volatile
    var mode: PersonaMode = initialMode
        private set

    fun exportState(): String = mode.name

    fun importState(raw: String) {
        val clean = raw.trim()
        PersonaMode.entries.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let {
            mode = it
        }
    }

    fun handleCommand(input: String): String? {
        val key = MemoryLearning.key(input).removePrefix("leo ").trim()
        return when {
            key in setOf(
                "activa modo eddy",
                "modo eddy",
                "ponete en modo eddy",
                "actua como eddy",
                "activa personalidad eddy",
            ) -> {
                mode = PersonaMode.EDDY_CLASSIC
                "¡Modo Eddy activado! Ingenio al 100 %, sistemas biónicos en línea y cero dependencia de la nube. ¿Qué misión tenemos hoy?"
            }

            key in setOf(
                "activa modo mision",
                "modo mision",
                "modo laboratorio",
                "centro de mando",
                "activa centro de mando",
            ) -> {
                mode = PersonaMode.MISSION_CONTROL
                "[CENTRO DE MANDO LEO] Modo Misión activado. Telemetría, protocolos de laboratorio y respuesta táctica listos."
            }

            key in setOf(
                "activa modo enfoque",
                "modo enfoque",
                "modo serio",
                "modo empatico",
                "activa modo empatico",
            ) -> {
                mode = PersonaMode.EMPATHIC_FOCUS
                "Modo Enfoque Empático activado. Respuestas claras, serenas y directas sin distracciones."
            }

            key in setOf(
                "en que modo estas",
                "cual es tu modo actual",
                "que personalidad tenes activa",
            ) -> {
                "Estoy operando en ${mode.label}, 100 % soberano en tu dispositivo."
            }

            else -> null
        }
    }

    /**
     * Modula una respuesta con el sello de personalidad activo, garantizando que nunca se use
     * tono burlón si el usuario prohibió bromas (`humorAllowed == false`) o si atraviesa
     * un estado emocional delicado (`distressed == true`).
     */
    fun styleReply(
        baseReply: String,
        userName: String? = null,
        humorAllowed: Boolean = true,
        distressed: Boolean = false,
    ): String {
        if (baseReply.isBlank()) return baseReply
        val cleanName = userName?.trim()?.takeIf { it.isNotBlank() }
        if (distressed) {
            return baseReply
        }
        return when (mode) {
            PersonaMode.MISSION_CONTROL -> {
                if (baseReply.startsWith("[CENTRO DE MANDO")) baseReply
                else "[CENTRO DE MANDO LEO] $baseReply"
            }

            PersonaMode.EDDY_CLASSIC -> {
                if (!humorAllowed) return baseReply
                val opener = if (cleanName != null) {
                    "Pan comido, $cleanName:"
                } else {
                    "Demasiado fácil para mis circuitos:"
                }
                if (baseReply.startsWith(opener) || baseReply.startsWith("[CENTRO")) baseReply
                else "$opener $baseReply"
            }

            PersonaMode.EMPATHIC_FOCUS -> baseReply
        }
    }

    /**
     * Calcula los parámetros acústicos y visuales para que la voz de LEO y el pulso del emblema
     * reflejen emoción real y ritmo de habla al estilo Eddy.
     */
    fun prosodyFor(emotion: Emotion? = null): ExpressiveProsody {
        if (emotion != null) {
            return when (emotion) {
                Emotion.EXCITEMENT, Emotion.JOY, Emotion.AMUSEMENT ->
                    ExpressiveProsody(pitchMultiplier = 1.08f, rateMultiplier = 1.10f, emblemPulseHz = 2.6f, vocalToneTag = "chispa_alegre")
                Emotion.SADNESS, Emotion.TIREDNESS, Emotion.DISAPPOINTMENT ->
                    ExpressiveProsody(pitchMultiplier = 0.94f, rateMultiplier = 0.92f, emblemPulseHz = 0.9f, vocalToneTag = "calma_empatica")
                Emotion.WORRY, Emotion.FEAR, Emotion.NERVOUSNESS, Emotion.FRUSTRATION ->
                    ExpressiveProsody(pitchMultiplier = 0.97f, rateMultiplier = 0.95f, emblemPulseHz = 1.3f, vocalToneTag = "apoyo_sereno")
                else -> defaultProsodyForMode()
            }
        }
        return defaultProsodyForMode()
    }

    private fun defaultProsodyForMode(): ExpressiveProsody = when (mode) {
        PersonaMode.EDDY_CLASSIC ->
            ExpressiveProsody(pitchMultiplier = 1.04f, rateMultiplier = 1.08f, emblemPulseHz = 2.1f, vocalToneTag = "eddy_ingenioso")
        PersonaMode.MISSION_CONTROL ->
            ExpressiveProsody(pitchMultiplier = 0.98f, rateMultiplier = 1.12f, emblemPulseHz = 2.4f, vocalToneTag = "mision_tactica")
        PersonaMode.EMPATHIC_FOCUS ->
            ExpressiveProsody(pitchMultiplier = 1.00f, rateMultiplier = 1.00f, emblemPulseHz = 1.2f, vocalToneTag = "enfoque_claro")
    }
}
