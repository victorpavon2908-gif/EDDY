package com.niko.assistant.ai

import com.niko.assistant.memory.MemoryLearning

/** Identity questions stay local and cannot be overwritten by an old learned answer. */
object NikoIdentity {
    /**
     * Old persisted branding is migrated before either speech engine sees it.
     * Screen formatting is intentionally not changed; this path is speech-only.
     */
    fun forSpeech(text: String): String {
        var value = LeoBrand.publicText(text)
        value = value
            // Preserve the visible label of Markdown links/images, but never read the URL.
            .replace(Regex("!\\[([^]]*)]\\((?:https?://)?[^)]*\\)"), "$1")
            .replace(Regex("\\[([^]]+)]\\((?:https?://)?[^)]*\\)"), "$1")
            // Citations and raw URLs belong on screen, not in spoken audio.
            .replace(Regex("\\[\\d{1,3}]"), "")
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "")
            // Remove headings/list markers before stripping inline emphasis markers.
            .replace(Regex("(?m)^\\s*(?:[-+•]|#{1,6})\\s+"), "")
            .replace("```", "")
            .replace("`", "")
            .replace("**", "")
            .replace("__", "")
            .replace("~~", "")
            .replace("*", "")
            .replace("_", " ")
            .replace(Regex("(?m)^\\s*#*\\s*"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return value
    }

    fun replyTo(
        input: String,
        adaptiveLearningEnabled: Boolean = true,
        trainingUpdates: Long = 0L,
        learnedCorrections: Int = 0,
    ): String? {
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

            asksAboutLearning(question) && adaptiveLearningEnabled -> {
                val progress = if (trainingUpdates > 0L) {
                    "Mi red adaptativa ya recibió $trainingUpdates actualizaciones supervisadas y aprendió $learnedCorrections correcciones de comandos."
                } else {
                    "Mi red adaptativa todavía está iniciando y aún no registra actualizaciones."
                }
                "Sí. Entreno de verdad una red pequeña en el teléfono: adapta sus pesos con interacciones que puedo clasificar con seguridad, y tus correcciones explícitas pueden convertirse en comandos locales reutilizables. $progress También uso un MicroGPT local ya entrenado para conversación breve y guardo preferencias y recuerdos útiles. No reentreno el modelo generativo completo en cada charla porque eso sería demasiado pesado e inestable para el teléfono."
            }

            asksAboutLearning(question) ->
                "Mi aprendizaje adaptativo está desactivado ahora mismo. Sigo usando mi MicroGPT local y la memoria para lo que me enseñés explícitamente, pero no adapto el clasificador de pedidos hasta que activés Aprendizaje en Ajustes."

            else -> null
        }
    }

    private fun asksAboutDeveloper(question: String): Boolean =
        Regex("\\b(?:quien|como se llama)\\s+(?:es\\s+)?(?:tu\\s+)?(?:desarrollador|creador|programador)\\b").containsMatchIn(question) ||
            Regex("\\b(?:desarrollador|creador|programador)\\s+de\\s+leo\\b").containsMatchIn(question) ||
            Regex("\\bquien\\b.{0,32}\\bte\\s+(?:desarrollo|creo|programo|diseno|hizo)\\b").containsMatchIn(question)

    fun isLearningQuestion(input: String): Boolean {
        val question = MemoryLearning.key(input)
            .removePrefix("leo ")
            .removePrefix("lio ")
            .removePrefix("niko ")
            .removePrefix("nico ")
        return asksAboutLearning(question)
    }

    private fun asksAboutLearning(question: String): Boolean {
        if (question in setOf("aprendes", "como aprendes", "vos aprendes", "tu aprendes", "vas aprendiendo", "podes aprender", "puedes aprender")) return true
        val learning = Regex("\\b(?:aprend[a-z]*|entren[a-z]*|adaptas?|adaptando|mejoras?|mejorando)\\b")
        val relationship = Regex("\\b(?:conmigo|con migo|de mi|sobre mi|al usar|con el uso|interactu[a-z]*|hablamos|convers[a-z]*|cada charla|mis pedidos|mis preferencias|con el tiempo)\\b")
        val selfReference = Regex("\\b(?:vos|tu|te)\\b")
        return learning.containsMatchIn(question) && (relationship.containsMatchIn(question) || selfReference.containsMatchIn(question))
    }
}
