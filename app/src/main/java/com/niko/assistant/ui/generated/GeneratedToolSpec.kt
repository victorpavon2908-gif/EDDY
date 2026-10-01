package com.niko.assistant.ui.generated

import org.json.JSONArray
import org.json.JSONObject

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
            "text", "text_input", "number_input", "counter", "toggle",
            "checklist", "list", "timer", "calculator", "metric",
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
            val subtitle = root.optString("subtitle").trim().take(140)
            val array = root.getJSONArray("components")
            require(array.length() in 1..12)
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
                    val text = node.optString("text").trim().take(500)
                    val initialNumber = node.optDouble("initial", 0.0)
                        .takeIf { it.isFinite() && it in -1_000_000.0..1_000_000.0 } ?: 0.0
                    val itemsJson = node.optJSONArray("items")
                    val items = if (itemsJson == null) emptyList() else buildList {
                        for (i in 0 until minOf(itemsJson.length(), 20)) {
                            val value = itemsJson.optString(i).trim().take(120)
                            if (value.isNotBlank()) add(value)
                        }
                    }
                    add(
                        GeneratedToolComponent(
                            id = id,
                            type = type,
                            label = label.ifBlank { defaultLabel(type) },
                            text = text,
                            initial = initialNumber,
                            items = items,
                        ),
                    )
                }
            }
            GeneratedToolSpec(title = title, subtitle = subtitle, components = components)
        }.getOrNull()

        private fun defaultLabel(type: String): String = when (type) {
            "text" -> "Información"
            "text_input" -> "Texto"
            "number_input" -> "Número"
            "counter" -> "Contador"
            "toggle" -> "Opción"
            "checklist" -> "Lista"
            "list" -> "Elementos"
            "timer" -> "Cronómetro"
            "calculator" -> "Calculadora"
            "metric" -> "Dato"
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
    val items: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type)
        .put("label", label)
        .put("text", text)
        .put("initial", initial)
        .put("items", JSONArray(items))
}
