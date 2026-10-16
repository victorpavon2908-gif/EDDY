package com.niko.assistant.cognitive

import com.niko.assistant.memory.MemoryLearning

/** Explicit, bounded personal facts. No inferred moods or remote transmission. */
class LeoPersonalMemory(private val store: Store) {
    interface Store {
        fun read(key: String): String?
        fun write(key: String, value: String)
        fun delete(key: String)
    }
    data class Preference(val key: String, val value: String)

    fun reply(input: String): String? {
        val text = normalized(input)
        if (text == "mostra mis preferencias" || text == "muestra mis preferencias") {
            val facts = labels.mapNotNull { (key, label) -> store.read(key)?.let { "$label: $it" } }
            return if (facts.isEmpty()) "Todavía no guardaste preferencias de comida, bebida, música o lugares."
                else facts.joinToString("\n")
        }
        val slot = slots.entries.firstOrNull { (_, terms) -> terms.any { text.contains(it) } }?.key
        correction(input)?.let { (previous, replacement) ->
            val current = store.read("food")?.removePrefix("la ")?.removePrefix("el ")
            if (current != previous.removePrefix("la ").removePrefix("el ")) return null
            safe("food", replacement)?.let {
                store.write(it.key, it.value)
                return "Actualicé tu comida favorita: ${it.value}. La anterior ya no figura como tu preferencia actual."
            }
        }
        parse(input)?.let {
            store.write(it.key, it.value)
            return "Recordaré que tu ${labels.getValue(it.key)} es ${it.value}. Podés corregirla o pedirme que la olvide."
        }
        if (slot != null && text.startsWith("olvida mi ")) {
            store.delete(slot)
            return "Olvidé tu ${labels.getValue(slot)}."
        }
        val query = when {
            text == "que comida me gusta mas" -> "food"
            text.startsWith("cual es mi ") || text.startsWith("recordas mi ") -> slot
            else -> null
        } ?: return null
        return store.read(query)?.let { "Tu ${labels.getValue(query)} es $it." }
            ?: "Todavía no me dijiste tu ${labels.getValue(query)}."
    }

    companion object {
        private val slots = linkedMapOf(
            "food" to listOf("comida favorita"), "drink" to listOf("bebida favorita"),
            "music" to listOf("musica favorita"), "place" to listOf("lugar favorito"),
        )
        private val labels = mapOf("food" to "comida favorita", "drink" to "bebida favorita",
            "music" to "música favorita", "place" to "lugar favorito")
        private fun normalized(input: String) = MemoryLearning.key(input).removePrefix("leo ")
        fun parse(input: String): Preference? {
            if (input.contains('¿') || input.contains('?') || input.contains('@') || input.length > 300) return null
            val text = normalized(input)
            for ((key, terms) in slots) for (term in terms) {
                Regex("^mi $term es (.+)$").matchEntire(text)?.let { return safe(key, it.groupValues[1]) }
            }
            return null
        }
        fun correction(input: String): Pair<String, String>? {
            if (input.any { it in "¿?@" } || input.length > 300) return null
            return Regex("^ya no me gusta (.+) ahora prefiero (.+)$").matchEntire(normalized(input))?.let {
                it.groupValues[1] to it.groupValues[2]
            }
        }
        fun isStatement(input: String) = parse(input) != null || correction(input) != null
        private fun safe(key: String, value: String): Preference? {
            if (value.length !in 1..120 || value.any { it.isDigit() } ||
                listOf("clave", "contrasena", "token", "secreto", "@", "http").any { value.contains(it) }) return null
            return Preference(key, value)
        }
    }
}
