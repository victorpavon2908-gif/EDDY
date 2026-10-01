package com.niko.assistant.ai

import org.json.JSONArray
import org.json.JSONObject

/** Optional synthesis of evidence already retrieved; it cannot create new source links. */
internal object ResearchSynthesis {
    fun payload(question: String, evidence: NikoAiReply): JSONObject = JSONObject()
        .put("max_completion_tokens", 1_200).put("stream", false)
        .put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", """
                Respondé en español natural usando únicamente la evidencia adjunta.
                Formulá una respuesta directa, breve y clara; no recites los extractos ni describás el proceso de búsqueda.
                La primera oración debe contestar la pregunta. Después agregá solo el motivo o contexto más importante.
                En una pregunta normal usá 2 a 4 oraciones. Si el usuario pide opciones concretas como empleos,
                vacantes, lugares, eventos o alternativas, podés dar hasta 5 hallazgos cortos con nombre, dato útil
                y ubicación o fecha cuando la evidencia los incluya.
                Conservá cifras, precios, unidades, nombres y fechas. Si varias fuentes coinciden, integrá el dato.
                Si discrepan, señalalo brevemente. No agregués datos que no estén respaldados por la evidencia.
                Soná humano y fluido: evitá tono de informe, encabezados, muletillas robóticas y frases como
                "según el artículo", "según la búsqueda", "encontré", "investigué", "resultado" o "respuesta breve".
                Cerrá con UNA pregunta corta y útil que continúe naturalmente el tema, por ejemplo
                "¿Querés que te investigue también los salarios y requisitos?" o
                "¿Querés que te busque las vacantes más recientes por ciudad?".
                Esa pregunta debe proponer un siguiente paso específico al tema, nunca una frase genérica.
                Devolvé SOLO JSON: {"resumen":[{"texto":"...","fuentes":[1]}],"detalles":[{"texto":"...","fuentes":[2]}],"seguimiento":"..."}.
                Cada afirmación lleva los números de fuentes que la respaldan. Máximo 2 elementos de
                resumen y 5 de detalles, con una sola oración por elemento. Sin enlaces ni Markdown.
            """.trimIndent()))
            .put(JSONObject().put("role", "user").put("content", JSONObject()
                .put("pregunta", question.take(500)).put("evidencia", evidence.researchContext.ifBlank { evidence.text }.take(9_000))
                .put("fuentes", JSONArray(evidence.sources.mapIndexed { index, source ->
                    JSONObject().put("numero", index + 1).put("titulo", source.title)
                })).toString())))

    fun apply(raw: String, original: NikoAiReply): NikoAiReply? = runCatching {
        val json = JSONObject(raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        fun section(name: String, max: Int): List<String> {
            val array = json.getJSONArray(name)
            require(array.length() in 1..max)
            return (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val text = item.getString("texto").trim()
                require(text.length in 25..700 && !Regex("(?i)https?://|www\\.").containsMatchIn(text))
                // Citation numbers come exclusively from the validated array below.
                require(!Regex("\\[\\d+\\]").containsMatchIn(text))
                val refs = item.getJSONArray("fuentes")
                require(refs.length() in 1..original.sources.size)
                val numbers = (0 until refs.length()).map { refs.getInt(it) }.distinct()
                require(numbers.all { it in 1..original.sources.size })
                "$text ${numbers.joinToString(" ") { "[$it]" }}"
            }
        }
        val summary = section("resumen", 2)
        val details = section("detalles", 5)
        val followUp = json.optString("seguimiento").trim()
            .takeIf { it.length in 12..220 && !Regex("(?i)https?://|www\\.|\\[\\d+\\]").containsMatchIn(it) }
            ?.let { if (it.endsWith("?")) it else "$it?" }
            .orEmpty()
        val body = (summary + details).joinToString(" ").replace(Regex("\\s+"), " ").trim()
        val concise = listOf(body, followUp).filter { it.isNotBlank() }.joinToString(" ")
        original.copy(
            text = concise,
            evidence = "Respuesta formulada a partir de evidencia web citada. " + original.evidence,
        )
    }.getOrNull()
}
