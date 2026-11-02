package com.niko.assistant.modes

import java.text.Normalizer
import java.util.Locale

/** Explicit operating profiles; speed changes budgets, never evidence or device permissions. */
enum class LeoMode(
    val id: String,
    val title: String,
    val description: String,
    val ink: Long,
    val background: Long,
    val discoveryMs: Long,
    val searchMs: Long,
    val queryCount: Int,
    val pageCount: Int,
    val sourceCount: Int,
    val instruction: String,
) {
    NORMAL("normal", "Normal", "Equilibrio entre conversación, detalle y búsqueda.",
        0xFF172B3A, 0xFFFFFFFF, 6500, 14000, 3, 8, 6,
        "Mantené un tono claro y cercano, con el detalle solicitado."),
    SUPER("super", "Super Leo", "Respuestas breves, voz ágil y menos espera.",
        0xFFC62828, 0xFFFFF1F1, 4000, 10000, 1, 6, 3,
        "Respondé al punto, con tono serio y pocas frases. Conservá datos esenciales, límites y precauciones."),
    RESEARCH("investigador", "Investigador", "Más fuentes, contexto y explicaciones amplias.",
        0xFF1558A6, 0xFFF0F6FF, 8500, 18000, 3, 8, 6,
        "Explicá con orden, ejemplos y límites. Separá datos de inferencias; no inventés fuentes."),
    CREATIVE("creativo", "Creativo", "Ideas variadas, ejemplos y una voz más expresiva.",
        0xFF7635AD, 0xFFF8F1FF, 6500, 14000, 3, 8, 6,
        "Proponé ideas variadas y ejemplos concretos. Identificá la ficción y no inventés hechos ni fuentes."),
    ;

    val serious: Boolean get() = this == SUPER || this == RESEARCH
    val inferenceTimeoutMs: Long get() = if (this == SUPER) 20_000 else 185_000
    fun tokenBudget(usual: Int): Int = if (this == SUPER) minOf(usual, 256) else usual

    companion object {
        fun fromId(id: String?): LeoMode = entries.firstOrNull { it.id == id } ?: NORMAL
        private fun key(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

        /** Whole utterances only: quotations, questions, searches and negations never change a mode. */
        fun requested(input: String): LeoMode? {
            if (input.any { it in "\"“”«»" }) return null
            val text = key(input).replace(Regex("^(?:leo|eddy)\\s+"), "")
                .replace(Regex("^por favor\\s+|\\s+por favor$"), "")
            if (text in setOf("desactiva modo super leo", "desactiva modo superleo", "desactiva super leo",
                    "desactiva modo investigador", "desactiva modo creativo", "desactiva los modos")) return NORMAL
            val target = Regex("^(?:activa(?:r)?|pon(?:te|ete)?(?: en)?|cambia a|cambiar a|vuelve a(?:l)?|regresa a(?:l)?) (?:el )?(?:modo )?(.+)$")
                .matchEntire(text)?.groupValues?.get(1) ?: return null
            return when (target) {
                "super leo", "superleo" -> SUPER
                "investigador", "investigacion", "leo investigador" -> RESEARCH
                "creativo", "leo creativo" -> CREATIVE
                "normal", "leo normal" -> NORMAL
                else -> null
            }
        }
    }
}
