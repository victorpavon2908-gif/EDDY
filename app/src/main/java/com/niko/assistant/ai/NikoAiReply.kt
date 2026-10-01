package com.niko.assistant.ai

data class NikoWebSource(val title: String, val url: String)

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
)
