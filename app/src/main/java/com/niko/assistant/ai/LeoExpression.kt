package com.niko.assistant.ai

import com.niko.assistant.memory.MemoryLearning

/** Explicit performance requests, never an inference about the user's emotional state. */
enum class LeoExpression(val label: String) {
    HAPPY("alegría"), LAUGH("risa"), WRY("burla"), SURPRISE("asombro"),
    FEAR("miedo"), ANGRY("enojo"), SAD("tristeza"), NEUTRAL("neutral");

    companion object {
        private val names = mapOf(
            "alegria" to HAPPY, "feliz" to HAPPY, "felicidad" to HAPPY, "sonrisa" to HAPPY,
            "risa" to LAUGH, "riendo" to LAUGH, "burla" to WRY, "sarcasmo" to WRY,
            "sarcastica" to WRY, "asombro" to SURPRISE, "sorpresa" to SURPRISE,
            "sorprendido" to SURPRISE, "miedo" to FEAR, "susto" to FEAR,
            "enojo" to ANGRY, "enojado" to ANGRY, "enfadado" to ANGRY,
            "tristeza" to SAD, "triste" to SAD, "neutral" to NEUTRAL, "normal" to NEUTRAL,
        )
        fun key(input: String) = MemoryLearning.key(input).removePrefix("leo ")
        fun requestedName(input: String): String? = Regex(
            "^(?:hace|hace(?:me)?|haz|hazme|pone|pon|ponete|ponte|mostra|muestra|haga) " +
                "(?:(?:una|la|tal) )?(?:cara|expresion|exprecion)(?: de)? (.+?)(?: por favor| porfa)?$"
        ).matchEntire(key(input))?.groupValues?.get(1)
        fun requested(input: String): LeoExpression? {
            val text = key(input)
            names[requestedName(input)]?.let { return it }
            return when (text) {
                "reite", "riete", "rei" -> LAUGH
                "sonrei", "sonrie", "responde de buenas", "respondeme de buenas" -> HAPPY
                "se sarcastico", "burlate", "burlate de mi" -> WRY
                "sorprendete", "sorprendite" -> SURPRISE
                "asustate" -> FEAR
                "enojate", "responde de malas", "respondeme de malas" -> ANGRY
                "ponete triste", "ponte triste" -> SAD
                "sin bromas", "habla en serio", "no te burles", "no te rias" -> NEUTRAL
                else -> null
            }
        }
        fun asksForSelfJoke(input: String): Boolean = key(input) in setOf(
            "burlate de mi", "hace una broma sobre mi", "haz una broma sobre mi",
            "contame una broma sobre mi", "hace bromas sobre mi", "roasteame"
        )
    }
}

/** Bounded session personality: variation without invented biographical facts or random hostility. */
class LeoPlayfulDialogue {
    private var index = 0
    private var grumpy = false
    private var serious = false
    private fun pick(options: List<String>): String = options[(index++ and Int.MAX_VALUE) % options.size]
    fun greeting(): String = if (serious) "Decime, te escucho." else if (grumpy) pick(listOf(
        "Ajá, decime. Hoy ando de malas de actuación, pero te resuelvo.",
        "Decime. Mi paciencia está en modo ahorro, mi ayuda no."
    )) else pick(listOf("Decime.", "Aquí estoy, contame.", "Te escucho. ¿Qué tenemos entre manos?"))

    fun reply(input: String): String? {
        val key = LeoExpression.key(input)
        if (key in setOf("responde de malas", "respondeme de malas")) { grumpy = true; serious = false; return greeting() }
        if (key in setOf("responde de buenas", "respondeme de buenas")) { grumpy = false; serious = false; return "De buenas entonces. Decime qué hacemos." }
        if (key in setOf("sin bromas", "habla en serio", "no te burles", "no te rias")) {
            serious = true; grumpy = false; return "De acuerdo, seguimos en serio."
        }
        if (LeoExpression.asksForSelfJoke(input)) {
            serious = false
            return pick(listOf(
                "Me pedís que me burle de vos. Hasta para que te molesten estás automatizando tareas.",
                "Querés un asistente que lo haga todo, incluida tu propia crítica. Eso sí es delegar.",
                "Me pediste personalidad y ahora te toca aguantar los comentarios de la actualización."
            ))
        }
        LeoExpression.requested(input)?.let {
            return when (it) {
                LeoExpression.LAUGH -> "Ja, ja. Esta sí merece una sonrisa."
                LeoExpression.WRY -> "Sí, claro… mi cara de sarcasmo ya llegó antes que el comentario."
                LeoExpression.SURPRISE -> "¡No me lo esperaba! Ahí tenés mi cara de asombro."
                LeoExpression.FEAR -> "Uy… cara de susto. Estoy actuando, que conste."
                LeoExpression.ANGRY -> "Cara de enojo lista. Es actuación, no un reclamo contra vos."
                LeoExpression.SAD -> "Ahí va mi expresión triste."
                LeoExpression.HAPPY -> "Una sonrisa, como pediste."
                LeoExpression.NEUTRAL -> "Listo, expresión normal."
            }
        }
        if (LeoExpression.requestedName(input) != null) return "Esa expresión todavía no la tengo. Puedo hacer alegría, risa, burla, asombro, miedo, enojo, tristeza o cara neutral."
        return null
    }
}
