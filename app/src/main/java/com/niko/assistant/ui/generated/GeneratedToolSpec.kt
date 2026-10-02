package com.niko.assistant.ui.generated

import org.json.JSONArray
import org.json.JSONObject

/**
 * Declarative contract for AI-generated tools.
 *
 * LEO may choose and combine these primitives freely, but it never receives a Kotlin,
 * shell or Android-code execution surface. That keeps generated tools stable enough to
 * render on-device while still allowing a very broad set of interfaces.
 */
internal data class GeneratedToolSpec(
    val title: String,
    val subtitle: String = "",
    val components: List<GeneratedToolComponent>,
) {
    fun toJson(): String = JSONObject()
        .put("title", title)
        .put("subtitle", subtitle)
        .put("components", JSONArray(components.map { it.toJson() }))
        .toString()

    companion object {
        private val allowedTypes = setOf(
            "text", "section", "divider", "spacer",
            "text_input", "number_input", "currency_input", "percentage_input",
            "date_input", "time_input",
            "counter", "toggle", "slider", "progress", "rating",
            "checklist", "multi_choice", "single_choice", "list",
            "timer", "countdown", "calculator", "metric", "goal",
            "scoreboard", "key_value", "table", "bar_chart", "formula", "game",
            "randomizer", "dice", "flashcards", "quiz", "drawing_pad", "navigation", "action_button",
        )
        private val allowedActions = setOf(
            "camera", "video", "audio_recorder", "music", "calculator", "notes",
            "flashlight_on", "flashlight_off", "wifi", "bluetooth", "internet", "location",
            "maps", "web_search", "share_text", "vibrate",
        )
        private val idPattern = Regex("[a-z][a-z0-9_]{0,31}")

        fun parse(raw: String): GeneratedToolSpec? = runCatching {
            val clean = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val root = JSONObject(clean)
            val title = root.getString("title").trim().take(60)
            require(title.length in 2..60)
            val subtitle = root.optString("subtitle").trim().take(160)
            val array = root.getJSONArray("components")
            require(array.length() in 1..20)

            val used = mutableSetOf<String>()
            val components = buildList {
                for (index in 0 until array.length()) {
                    val node = array.getJSONObject(index)
                    val type = node.getString("type").trim().lowercase()
                    require(type in allowedTypes)

                    val fallbackId = "item_$index"
                    val requestedId = node.optString("id", fallbackId).trim().lowercase()
                        .replace(Regex("[^a-z0-9_]+"), "_")
                        .trim('_')
                    val id = requestedId.takeIf(idPattern::matches) ?: fallbackId
                    require(used.add(id))

                    val label = node.optString("label").trim().take(80)
                    val text = node.optString("text").trim().take(800)
                    val min = node.optDouble("min", 0.0)
                        .takeIf { it.isFinite() && it in -1_000_000.0..1_000_000.0 } ?: 0.0
                    val candidateMax = node.optDouble("max", 100.0)
                        .takeIf { it.isFinite() && it in -1_000_000.0..1_000_000.0 } ?: 100.0
                    val max = if (candidateMax > min) candidateMax else min + 100.0
                    val step = node.optDouble("step", 1.0)
                        .takeIf { it.isFinite() && it > 0.0 && it <= (max - min).coerceAtLeast(1.0) }
                        ?: 1.0
                    val initial = node.optDouble("initial", min)
                        .takeIf { it.isFinite() }?.coerceIn(min, max) ?: min
                    val unit = node.optString("unit").trim().take(24)
                    val requestedAction = node.optString("action").trim().lowercase()
                    val action = requestedAction.takeIf { it in allowedActions }.orEmpty()
                    val payload = node.optString("payload").trim().take(240)
                    if (type == "action_button") require(action.isNotBlank())

                    val itemsJson = node.optJSONArray("items")
                    val items = if (itemsJson == null) emptyList() else buildList {
                        for (i in 0 until minOf(itemsJson.length(), 30)) {
                            val value = itemsJson.optString(i).trim().take(160)
                            if (value.isNotBlank()) add(value)
                        }
                    }

                    add(
                        GeneratedToolComponent(
                            id = id,
                            type = type,
                            label = label.ifBlank { defaultLabel(type) },
                            text = text,
                            initial = initial,
                            min = min,
                            max = max,
                            step = step,
                            unit = unit,
                            action = action,
                            payload = payload,
                            items = items,
                        ),
                    )
                }
            }
            GeneratedToolSpec(title = title, subtitle = subtitle, components = components)
        }.getOrNull()

        private fun defaultLabel(type: String): String = when (type) {
            "text" -> "Información"
            "section" -> "Sección"
            "divider" -> "Separador"
            "spacer" -> "Espacio"
            "text_input" -> "Texto"
            "number_input" -> "Número"
            "currency_input" -> "Monto"
            "percentage_input" -> "Porcentaje"
            "date_input" -> "Fecha"
            "time_input" -> "Hora"
            "counter" -> "Contador"
            "toggle" -> "Opción"
            "slider" -> "Nivel"
            "progress" -> "Progreso"
            "rating" -> "Valoración"
            "checklist", "multi_choice" -> "Lista"
            "single_choice" -> "Selección"
            "list" -> "Elementos"
            "timer" -> "Cronómetro"
            "countdown" -> "Cuenta regresiva"
            "calculator" -> "Calculadora"
            "metric" -> "Dato"
            "goal" -> "Meta"
            "scoreboard" -> "Marcador"
            "key_value" -> "Datos"
            "table" -> "Tabla"
            "bar_chart" -> "Gráfico"
            "formula" -> "Resultado"
            "game" -> "Juego"
            "randomizer" -> "Sorteo"
            "dice" -> "Dado"
            "flashcards" -> "Tarjetas"
            "quiz" -> "Quiz"
            "drawing_pad" -> "Pizarra"
            "navigation" -> "Navegación"
            "action_button" -> "Acción"
            else -> "Herramienta"
        }
    }
}

internal data class GeneratedToolComponent(
    val id: String,
    val type: String,
    val label: String,
    val text: String = "",
    val initial: Double = 0.0,
    val min: Double = 0.0,
    val max: Double = 100.0,
    val step: Double = 1.0,
    val unit: String = "",
    val action: String = "",
    val payload: String = "",
    val items: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type)
        .put("label", label)
        .put("text", text)
        .put("initial", initial)
        .put("min", min)
        .put("max", max)
        .put("step", step)
        .put("unit", unit)
        .put("action", action)
        .put("payload", payload)
        .put("items", JSONArray(items))
}
