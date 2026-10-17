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
        val recommendation = when (text) {
            "que me recomendas comer", "que me recomiendas comer", "que comemos hoy", "que puedo comer" -> "food"
            "que me recomendas beber", "que me recomiendas beber", "que puedo beber" -> "drink"
            else -> null
        }
        if (recommendation != null) {
            return store.read(recommendation)?.let { "Podemos partir de $it: me dijiste que es tu ${labels.getValue(recommendation)}. Si querés algo distinto, decime qué tenés disponible." }
                ?: "Todavía no conozco tu ${labels.getValue(recommendation)}. ¿Qué te gusta y qué tenés disponible?"
        }
        val slot = slots.entries.firstOrNull { (_, terms) -> terms.any { text.contains(it) } }?.key
        correction(input)?.let { (previous, replacement) ->
            val matching = slots.keys.filter { key -> store.read(key)?.let(::canonicalValue) == canonicalValue(previous) }
            if (matching.size > 1) return "¿Querés cambiar tu comida, bebida, música o lugar favorito? Decime cuál."
            val key = matching.singleOrNull() ?: return null
            safe(key, replacement)?.let {
                store.write(it.key, it.value)
                return "Actualicé tu ${labels.getValue(key)}: ${it.value}. La anterior ya no figura como tu preferencia actual."
            }
            return "No cambié tu preferencia. Decime el nuevo valor sin información sensible ni suposiciones."
        }
        if ('?' !in input && '¿' !in input) {
            Regex("^ya no me gusta (.+)$").matchEntire(text)?.groupValues?.get(1)?.let { old ->
                val matching = slots.keys.filter { key -> store.read(key)?.let(::canonicalValue) == canonicalValue(old) }
                if (matching.size > 1) return "¿Cuál preferencia querés quitar: comida, bebida, música o lugar?"
                matching.singleOrNull()?.let { key ->
                    store.delete(key)
                    return "Dejé de considerar $old como tu ${labels.getValue(key)}."
                }
            }
        }
        if (assignmentValue(input)?.let { !isCertainSingleValue(it) } == true) {
            return "No cambié tus preferencias. Decime una preferencia concreta, por ejemplo: mi comida favorita es pizza."
        }
        parse(input)?.let {
            store.write(it.key, it.value)
            return "Recordaré que tu ${labels.getValue(it.key)} es ${it.value}. Podés corregirla o pedirme que la olvide."
        }
        val forgotten = slots.entries.singleOrNull { (_, terms) ->
            terms.any { text == "olvida mi $it" || text == "olvida mi $it por favor" }
        }?.key
        if (forgotten != null && input.none { it in "¿?" }) {
            store.delete(forgotten)
            return "Olvidé tu ${labels.getValue(forgotten)}."
        }
        /* No prefix matching for destructive commands: trailing text can negate
         * or quote the request. Queries must never erase a preference. */
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
                Regex("^mi $term es (.+)$").matchEntire(text)?.let { return safe(key, input.split(Regex("(?i)\\s+es\\s+"), limit = 2).last().trim().trimEnd('.', '!')) }
            }
            return null
        }
        fun correction(input: String): Pair<String, String>? {
            if (input.any { it in "¿?@" } || input.length > 300) return null
            return Regex("^ya no me gusta (.+) ahora prefiero (.+)$").matchEntire(normalized(input))?.let {
                it.groupValues[1] to input.split(Regex("(?i)\\bahora\\s+prefiero\\s+"), limit = 2).last().trim().trimEnd('.', '!')
            }
        }
        // Reserve attempted assignments too, so rejected uncertain values do not
        // fall through into the generic long-term learner as durable memories.
        fun isStatement(input: String) = assignmentValue(input) != null || correction(input) != null
        private fun canonicalValue(value: String) = MemoryLearning.key(value).removePrefix("la ").removePrefix("el ").removePrefix("los ").removePrefix("las ")
        private fun assignmentValue(input: String): String? {
            val text = normalized(input)
            return slots.values.flatten().firstNotNullOfOrNull { term ->
                Regex("^mi $term es (.+)$").matchEntire(text)?.groupValues?.get(1)
            }
        }
        private fun isCertainSingleValue(value: String): Boolean {
            val text = MemoryLearning.key(value)
            return !Regex("\\b(?:quizas|tal vez|creo que|no se|posiblemente|probablemente|puede ser)\\b").containsMatchIn(text) &&
                !Regex("\\b(?:pero|aunque|si fuera|si fuese)\\b").containsMatchIn(text) &&
                !slots.values.flatten().any { text.contains("mi $it") }
        }
        private fun safe(key: String, value: String): Preference? {
            val normalizedValue = MemoryLearning.key(value)
            if (!isCertainSingleValue(value) || value.length !in 1..120 || value.any { it.isDigit() } ||
                listOf("clave", "contrasena", "token", "secreto", "@", "http").any { normalizedValue.contains(it) }) return null
            return Preference(key, value)
        }
    }
}
