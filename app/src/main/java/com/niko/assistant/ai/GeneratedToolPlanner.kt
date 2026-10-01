package com.niko.assistant.ai

import android.content.Context
import com.niko.assistant.ui.generated.GeneratedToolComponent
import com.niko.assistant.ui.generated.GeneratedToolSpec
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

internal class GeneratedToolPlanner(context: Context) {
    private val appContext = context.applicationContext

    suspend fun generate(request: String): GeneratedToolSpec {
        val prompt = request.trim().take(180)
        if (prompt.isBlank()) return fallback("Herramienta personalizada")

        val apiKey = NikoAiSettings.apiKey(appContext)
        if (apiKey.isBlank()) return fallback(prompt)

        val system = """
            Diseñá una herramienta móvil simple para LEO a partir de la petición del usuario.
            No generés Kotlin, Java, JavaScript, comandos, URLs ni código ejecutable.
            Devolvé SOLO un objeto JSON válido con:
            {
              "title":"...",
              "subtitle":"...",
              "components":[
                {"id":"...", "type":"...", "label":"...", "text":"...", "initial":0, "items":[]}
              ]
            }
            Tipos permitidos: text, text_input, number_input, counter, toggle, checklist, list, timer, calculator, metric.
            Máximo 10 componentes. IDs en minúscula con letras, números y guion bajo.
            Usá componentes realmente útiles para la petición. Texto breve y natural en español.
            Para listas/checklists podés precargar hasta 8 items. Para contadores usá initial.
            No incluyás acciones del sistema, permisos, acceso a archivos, red, shell, procesos ni Android APIs.
        """.trimIndent()

        val base = JSONObject()
            .put("stream", false)
            .put("max_completion_tokens", 1_400)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", prompt)),
            )

        val models = listOf(GroqProtocol.QUALITY_MODEL, GroqProtocol.FAST_MODEL).distinct()
        val generated = withTimeoutOrNull(14_000L) {
            for (model in models) {
                val payload = JSONObject(base.toString()).put("model", model)
                val response = GroqHttpClient().complete(apiKey, payload)
                if (response.code !in 200..299) continue
                val answer = runCatching { GroqProtocol.answer(JSONObject(response.body)) }.getOrNull() ?: continue
                GeneratedToolSpec.parse(answer.text)?.let { return@withTimeoutOrNull it }
            }
            null
        }
        return generated ?: fallback(prompt)
    }

    private fun fallback(request: String): GeneratedToolSpec {
        val normalized = request.lowercase()
        val title = request
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .take(48)
            .ifBlank { "Herramienta personalizada" }

        val components = when {
            listOf("vuelta", "repeticion", "repetición", "serie", "conteo").any(normalized::contains) -> listOf(
                GeneratedToolComponent("contador", "counter", "Contador"),
                GeneratedToolComponent("tiempo", "timer", "Tiempo"),
            )
            listOf("gasto", "presupuesto", "dinero", "compra").any(normalized::contains) -> listOf(
                GeneratedToolComponent("monto", "number_input", "Monto"),
                GeneratedToolComponent("descripcion", "text_input", "Descripción"),
                GeneratedToolComponent("movimientos", "list", "Movimientos"),
                GeneratedToolComponent("total", "calculator", "Cálculo"),
            )
            listOf("estudio", "tareas", "pendiente", "checklist").any(normalized::contains) -> listOf(
                GeneratedToolComponent("objetivo", "text_input", "Objetivo"),
                GeneratedToolComponent("pasos", "checklist", "Pasos"),
                GeneratedToolComponent("sesion", "timer", "Sesión"),
            )
            else -> listOf(
                GeneratedToolComponent("nota", "text_input", "Dato principal"),
                GeneratedToolComponent("lista", "list", "Elementos"),
                GeneratedToolComponent("contador", "counter", "Contador"),
            )
        }
        return GeneratedToolSpec(
            title = title,
            subtitle = "Creada por LEO para esta petición",
            components = components,
        )
    }
}
