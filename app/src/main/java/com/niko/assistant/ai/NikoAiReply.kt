package com.niko.assistant.ai

data class NikoWebSource(val title: String, val url: String)

data class NikoRetrievedPassage(val sourceIndex: Int, val text: String, val pageRead: Boolean = true)

data class NikoAiReply(
    val text: String,
    val webUsed: Boolean,
    val sources: List<NikoWebSource>,
    val evidence: String = "",
    /**
     * Evidencia textual recuperada de las páginas. No se muestra directamente al usuario:
     * sirve para que el sintetizador formule una respuesta propia sin inventar datos.
     */
    val researchContext: String = "",
    /** Optional narration for non-speech artifacts such as source code. */
    val spokenText: String? = null,
    val passages: List<NikoRetrievedPassage> = emptyList(),
    /** Preguntas de seguimiento o sugerencias interactivas mostradas al usuario. */
    val suggestions: List<String> = emptyList(),
)
