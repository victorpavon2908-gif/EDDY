package com.niko.assistant.devicecontrol

import java.text.Normalizer
import java.util.Locale

/**
 * Contexto visual local para preguntas sobre la pantalla actual.
 *
 * Inspirado en el patrón multimodal de Parlor Vision (Apache-2.0), pero reimplementado
 * para Android sin servidor, Ollama ni envío de capturas: NIKO usa únicamente el árbol
 * de Accessibility del teléfono y lo entrega al LLM local cuando el usuario lo pide.
 */
object NikoVisualContext {
    data class Capture(
        val evidence: String = "",
        val problem: String? = null,
    ) {
        val available: Boolean get() = problem == null && evidence.isNotBlank()
    }

    /**
     * Solo adjunta contexto visual cuando la petición realmente hace referencia a lo
     * que hay en pantalla. Así evitamos gastar CPU y exponer UI innecesariamente.
     */
    fun wantsScreenContext(text: String): Boolean {
        val value = key(text)
        if (value.isBlank()) return false

        if (value in EXACT_VISUAL_REQUESTS) return true

        val mentionsScreen = containsAny(
            value,
            "pantalla", "lo que tengo abierto", "lo que esta abierto", "esta app", "esta aplicacion",
            "lo que sale aqui", "lo que aparece aqui", "esto que sale", "esto que aparece",
        )
        val visualVerb = containsAny(
            value,
            "mira", "mira esto", "mirame", "revisa", "analiza", "lee", "leeme", "que ves",
            "que dice", "que aparece", "que sale", "que hay", "ayudame con esto", "explicame esto",
        )
        val deictic = containsAny(value, "esto", "aqui", "ahi", "esta pantalla", "esta app")

        return (mentionsScreen && visualVerb) || (visualVerb && deictic)
    }

    fun capture(): Capture {
        val service = NikoAccessibilityService.instance
            ?: return Capture(
                problem = "Para mirar lo que aparece en pantalla, activá LEO Device Control en Accesibilidad de Android.",
            )

        val snapshot = runCatching { service.snapshot(maxNodes = 110, maxDepth = 10) }
            .getOrElse {
                return Capture(problem = "No pude leer la pantalla actual. Probá de nuevo cuando la aplicación esté visible.")
            }

        if (snapshot.nodeCount <= 0 || snapshot.tree.isBlank()) {
            return Capture(
                problem = "La aplicación visible no expone contenido legible por Accesibilidad. Puedo manejar lo que Android sí me permita leer.",
            )
        }

        return Capture(
            evidence = buildString {
                appendLine("CONTEXTO VISUAL LOCAL DE LA PANTALLA ACTUAL")
                appendLine("Aplicación visible: ${snapshot.packageName.ifBlank { "desconocida" }}")
                appendLine("Fuente: árbol de Accesibilidad de Android; no es una captura de píxeles.")
                appendLine("No afirmes ver fotos, colores, iconos sin etiqueta ni elementos que no aparezcan abajo.")
                appendLine("Si la pregunta se puede responder con los controles/textos visibles, respondé de forma breve y concreta.")
                append(snapshot.tree.take(MAX_TREE_CHARS))
            },
        )
    }

    private fun key(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("(?i)\\b(?:niko|nico|nikko|nin|por favor|porfa|haceme el favor|hazme el favor)\\b"), " ")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsAny(value: String, vararg needles: String): Boolean = needles.any(value::contains)

    private val EXACT_VISUAL_REQUESTS = setOf(
        "que ves",
        "que estas viendo",
        "ves esto",
        "mira esto",
        "mirame esto",
        "que dice aqui",
        "lee esto",
        "leeme esto",
        "explicame esto",
        "ayudame con esto",
    )

    private const val MAX_TREE_CHARS = 7_500
}
