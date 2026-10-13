package com.niko.assistant.ai

import com.niko.assistant.memory.MemoryLearning

/** Local conversation repairs. Never turns an ambiguous reference into an Android action. */
object LeoDialoguePolicy {
    private fun key(input: String) = MemoryLearning.key(input).removePrefix("leo ")

    fun style(input: String): String? = when (key(input)) {
        "responde mas corto", "respondeme mas corto", "responde corto", "se breve", "prefiero respuestas cortas" -> "brief"
        "responde con mas detalle", "prefiero respuestas detalladas" -> "detailed"
        else -> null
    }

    fun clarification(input: String, previousAnswer: String = ""): String? {
        val text = key(input)
        return when {
            text in setOf("ayudame", "necesito ayuda", "me ayudas", "podes ayudarme", "puedes ayudarme") ->
                "Claro. ¿Qué necesitás resolver?"
            text in setOf("no me entendiste", "no entendiste", "eso no es lo que pedi", "eso no fue lo que dije") ->
                "No interpreté bien tu pedido. ¿Qué querías que hiciera?"
            text in setOf("explicame este error", "arregla este error", "revisa este codigo", "corrige este codigo") ->
                "Pasame el código o el mensaje exacto del error para revisar qué está fallando."
            text in setOf("abrilo", "borralo", "mandalo", "envialo", "hace eso", "haz eso", "mejor el otro") ->
                "¿A qué te referís exactamente? Prefiero confirmarlo antes de actuar."
            text in setOf("si", "dale", "segui", "continua", "eso", "explicamelo") && previousAnswer.isBlank() ->
                "Me falta el contexto. ¿Qué tema o tarea querés que retomemos?"
            text in setOf("explicamelo", "no entendi", "explicalo mas sencillo") ->
                "¿Qué parte querés que aclare? Así puedo explicártela sin repetir lo mismo."
            else -> null
        }
    }

    fun fallback(): String = "No logré interpretar bien lo que necesitás. ¿Podés decirlo de otra manera o darme un ejemplo?"

    /** Explicit brevity affects research narration, never stored evidence or action confirmations. */
    fun researchSpeech(text: String, style: String?): String {
        if (style != "brief" || text.length <= 420) return text
        val sentences = Regex("[^.!?]+[.!?](?:\\s|$)").findAll(text).map { it.value.trim() }.toList()
        val brief = sentences.take(3).joinToString(" ")
        if (brief.isBlank() || brief.length >= text.length) return text
        return "$brief El detalle completo queda en pantalla."
    }
}
