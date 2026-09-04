package com.niko.assistant.ai

import com.niko.assistant.memory.MemoryLearning

/** Identity questions stay local and cannot be overwritten by an old learned answer. */
object NikoIdentity {
    /** Old persisted branding is migrated before either speech engine sees it. */
    fun forSpeech(text: String): String = LeoBrand.publicText(text)
        .replace(Regex("\\[\\d{1,2}\\]"), "")
        .replace(Regex("(?m)^[•*#]+\\s*"), "")

    fun replyTo(input: String, adaptiveLearningEnabled: Boolean = true): String? {
        val question = MemoryLearning.key(input)
            .removePrefix("leo ")
            .removePrefix("lio ")
            .removePrefix("niko ")
            .removePrefix("nico ")
        return when {
            question in setOf("como te llamas", "cual es tu nombre", "quien eres", "quien sos", "que eres", "que sos") ->
                "Soy Leo, tu asistente personal. Decime qué necesitás."

            asksAboutDeveloper(question) ->
                "Mi desarrollador es ${LeoBrand.DEVELOPER_NAME}. Él creó y dirige el proyecto Leo."

            asksAboutLearning(question) && adaptiveLearningEnabled ->
                "Sí. Conforme interactuás conmigo, guardo localmente los datos y preferencias que me enseñás, recuerdo acciones completadas y adapto cómo clasifico tus pedidos. No reentreno el modelo base con cada charla; podés preguntarme qué recuerdo o decirme que borre mi memoria."

            asksAboutLearning(question) ->
                "Mi aprendizaje adaptativo está desactivado ahora mismo. Sigo usando la memoria local para lo que me enseñés explícitamente, pero no adapto el clasificador de pedidos hasta que activés Aprendizaje en Ajustes."

            else -> null
        }
    }

    private fun asksAboutDeveloper(question: String): Boolean =
        Regex("\\b(?:quien|como se llama)\\s+(?:es\\s+)?(?:tu\\s+)?(?:desarrollador|creador|programador)\\b").containsMatchIn(question) ||
            Regex("\\b(?:desarrollador|creador|programador)\\s+de\\s+leo\\b").containsMatchIn(question) ||
            Regex("\\bquien\\b.{0,32}\\bte\\s+(?:desarrollo|creo|programo|diseno|hizo)\\b").containsMatchIn(question)

    private fun asksAboutLearning(question: String): Boolean {
        if (question in setOf("aprendes", "como aprendes", "vos aprendes", "tu aprendes", "vas aprendiendo", "podes aprender", "puedes aprender")) return true
        val learning = Regex("\\b(?:aprende[rs]?|aprendiendo|aprendizaje|entrena[rs]?|entrenando|adaptas?|adaptando|mejoras?|mejorando|memoria)\\b")
        val interaction = Regex("\\b(?:conmigo|con migo|de mi|sobre mi|al usar|con el uso|interactu[a-z]*|hablamos|convers[a-z]*|cada charla|mis pedidos|mis preferencias|con el tiempo|funciona)\\b")
        return learning.containsMatchIn(question) && interaction.containsMatchIn(question)
    }
}
