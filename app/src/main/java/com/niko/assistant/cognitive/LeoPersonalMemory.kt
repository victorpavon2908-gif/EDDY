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

    fun humorAllowed(): Boolean? = store.read("humor_policy")?.toBooleanStrictOrNull()

    fun preferences(): List<Preference> = labels.keys.mapNotNull { key ->
        store.read(key)?.let { Preference(key, it) }
    }

    fun localContext(): String = preferences().joinToString("\n") {
        "${labels.getValue(it.key)}: ${it.value}"
    }.take(1600) + when (humorAllowed()) {
        false -> "\nPreferencia de trato: no hacer bromas sobre el usuario ni sarcasmo."
        true -> "\nPreferencia de trato: permite bromas respetuosas sobre sí mismo."
        null -> ""
    }

    fun reply(input: String): String? {
        val text = normalized(input)
        humorSetting(input)?.let { allowed ->
            store.write("humor_policy", allowed.toString())
            return if (allowed) "Guardé que permitís bromas sobre vos. Podés cambiarlo cuando querás."
                else "Guardé que no querés bromas sobre vos ni sarcasmo. Voy a respetarlo."
        }
        if (text == "olvida mi preferencia de humor" && input.none { it in "¿?" }) {
            store.delete("humor_policy")
            return "Olvidé tu preferencia de humor."
        }
        if (text == "cual es mi preferencia de humor") return when (humorAllowed()) {
            true -> "Permitís bromas sobre vos."
            false -> "Preferís que no haga bromas sobre vos ni sarcasmo."
            null -> "Todavía no guardaste una preferencia de humor."
        }
        if (text in setOf("mostra mis preferencias", "muestra mis preferencias", "cuales son mis preferencias", "que preferencias mias tenes guardadas", "que sabes sobre mis gustos")) {
            val facts = labels.mapNotNull { (key, label) -> store.read(key)?.let { "$label: $it" } }
            return if (facts.isEmpty()) "Todavía no guardaste preferencias personales. Podés enseñarme tu comida, música, pasatiempo o lenguaje de programación favorito."
                else facts.joinToString("\n")
        }
        val recommendation = when (text) {
            "que me recomendas comer", "que me recomiendas comer", "que comemos hoy", "que puedo comer" -> "food"
            "que me recomendas beber", "que me recomiendas beber", "que puedo beber", "que puedo tomar" -> "drink"
            "que me recomendas leer", "que me recomiendas leer", "que puedo leer" -> "book"
            "que me recomendas ver", "que me recomiendas ver", "que pelicula vemos" -> "movie"
            "que me recomendas escuchar", "que me recomiendas escuchar", "que musica ponemos" -> "music"
            "que me recomendas jugar", "que me recomiendas jugar" -> "game"
            else -> null
        }
        if (recommendation != null) {
            return store.read(recommendation)?.let { "Podemos partir de $it: me dijiste que es tu ${labels.getValue(recommendation)}. Si querés algo distinto, decime qué tenés disponible." }
                ?: "Todavía no conozco tu ${labels.getValue(recommendation)}. ¿Qué te gusta y qué tenés disponible?"
        }
        val slot = slots.entries.firstOrNull { (_, terms) -> terms.any { text.contains(it) } }?.key
        correction(input)?.let { (previous, replacement) ->
            val matching = slots.keys.filter { key -> store.read(key)?.let(::canonicalValue) == canonicalValue(previous) }
            if (matching.size > 1) return "¿Cuál de tus preferencias querés cambiar? Decime su nombre."
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
                if (matching.size > 1) return "¿Cuál de tus preferencias querés quitar? Decime su nombre."
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
            if (store.read(it.key)?.let(::canonicalValue) == canonicalValue(it.value)) {
                return "Ya recuerdo que tu ${labels.getValue(it.key)} es ${store.read(it.key)}."
            }
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
            text in setOf("que comida me gusta mas", "que comida prefiero", "que me gusta comer") -> "food"
            text in setOf("que bebida me gusta mas", "que bebida prefiero", "que me gusta beber", "que me gusta tomar") -> "drink"
            text in setOf("que musica me gusta mas", "que musica prefiero", "que musica escucho") -> "music"
            text in setOf("que lenguaje de programacion prefiero", "que lenguaje de programacion me gusta mas", "en que lenguaje programo") -> "programming_language"
            text in setOf("que deporte me gusta mas", "que deporte prefiero") -> "sport"
            text in setOf("que libro me gusta mas", "que libro prefiero") -> "book"
            text in setOf("que pelicula me gusta mas", "que pelicula prefiero") -> "movie"
            text in setOf("que juego me gusta mas", "que juego prefiero") -> "game"
            text in setOf("que color me gusta mas", "que color prefiero") -> "color"
            text in setOf("como me llamo", "cual es mi nombre") -> "name"
            text in setOf("cual es mi profesion", "de que trabajo", "a que me dedico") -> "profession"
            text in setOf("en que proyecto estoy", "cual es mi proyecto actual", "cual es mi proyecto principal") -> "project"
            text in setOf("que estudio", "cual es mi carrera") -> "study"
            text.startsWith("cual es mi ") || text.startsWith("recordas mi ") || text.startsWith("recuerdas mi ") -> slot
            else -> null
        } ?: return null
        return store.read(query)?.let { "Tu ${labels.getValue(query)} es $it." }
            ?: "Todavía no me dijiste tu ${labels.getValue(query)}."
    }

    companion object {
        private val slots = linkedMapOf(
            "food" to listOf("comida favorita", "comida preferida"),
            "drink" to listOf("bebida favorita", "bebida preferida"),
            "music" to listOf("musica favorita", "musica preferida"),
            "place" to listOf("lugar favorito", "lugar preferido"),
            "hobby" to listOf("pasatiempo favorito", "pasatiempo preferido"),
            "sport" to listOf("deporte favorito", "deporte preferido"),
            "book" to listOf("libro favorito", "libro preferido"),
            "movie" to listOf("pelicula favorita", "pelicula preferida"),
            "game" to listOf("juego favorito", "juego preferido"),
            "programming_language" to listOf("lenguaje de programacion favorito", "lenguaje de programacion preferido"),
            "color" to listOf("color favorito", "color preferido"),
            "city" to listOf("ciudad favorita", "ciudad preferida"),
            "goal" to listOf("meta principal", "objetivo principal"),
            "profession" to listOf("profesion", "ocupacion"),
            "project" to listOf("proyecto actual", "proyecto principal"),
            "study" to listOf("carrera", "area de estudio"),
            "schedule" to listOf("rutina favorita", "horario preferido"),
            "team" to listOf("equipo favorito", "equipo preferido"),
            "pet" to listOf("mascota"),
            "name" to listOf("nombre", "apodo"),
        )
        private val labels = mapOf(
            "food" to "comida favorita",
            "drink" to "bebida favorita",
            "music" to "música favorita",
            "place" to "lugar favorito",
            "hobby" to "pasatiempo favorito",
            "sport" to "deporte favorito",
            "book" to "libro favorito",
            "movie" to "película favorita",
            "game" to "juego favorito",
            "programming_language" to "lenguaje de programación favorito",
            "color" to "color favorito",
            "city" to "ciudad favorita",
            "goal" to "meta principal",
            "profession" to "profesión",
            "project" to "proyecto actual",
            "study" to "carrera",
            "schedule" to "rutina favorita",
            "team" to "equipo favorito",
            "pet" to "mascota",
            "name" to "nombre",
        )
        private fun normalized(input: String) = MemoryLearning.key(input).removePrefix("leo ")
            .replace(Regex("^(?:recorda|recuerda|aprende|guarda) que (?=(?:ahora )?mi )"), "")
            .replace(Regex("^ahora (?=mi )"), "")
        fun parse(input: String): Preference? {
            if (input.contains('¿') || input.contains('?') || input.contains('@') || input.length > 300) return null
            val text = normalized(input)
            Regex("^(?:me llamo|llamame) ([a-zA-ZáéíóúÁÉÍÓÚñÑ ]{2,40})$").matchEntire(text)?.let {
                val rawName = input.replace(Regex("(?i)^(?:leo[,\\s]+)?(?:me llamo|ll[aá]mame)\\s+"), "").trim().trimEnd('.', '!')
                return safe("name", rawName)
            }
            for ((key, terms) in slots) for (term in terms) {
                Regex("^mi $term es (.+)$").matchEntire(text)?.let {
                    return safe(key, input.split(Regex("(?i)\\s+es\\s+"), limit = 2).last().trim().trimEnd('.', '!'))
                }
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
        fun isStatement(input: String) = assignmentValue(input) != null || correction(input) != null || humorSetting(input) != null
        private fun humorSetting(input: String): Boolean? {
            if (input.any { it in "¿?" }) return null
            return when (normalized(input)) {
                "no hagas bromas sobre mi", "no te burles de mi", "no quiero sarcasmo", "prefiero que no hagas bromas" -> false
                "podes hacer bromas sobre mi", "puedes hacer bromas sobre mi", "permito bromas sobre mi" -> true
                else -> null
            }
        }
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
