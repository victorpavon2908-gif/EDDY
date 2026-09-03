package com.niko.assistant.ai

import com.niko.assistant.memory.MemoryLearning

/** Identity questions stay local and cannot be overwritten by an old learned answer. */
object NikoIdentity {
    /** Old persisted branding is migrated before either speech engine sees it. */
    fun forSpeech(text: String): String = LeoBrand.publicText(text)
        .replace(Regex("\\[\\d{1,2}\\]"), "")
        .replace(Regex("(?m)^[•*#]+\\s*"), "")

    fun replyTo(input: String): String? {
        val question = MemoryLearning.key(input)
            .removePrefix("leo ")
            .removePrefix("lio ")
            .removePrefix("niko ")
            .removePrefix("nico ")
        return if (question in setOf("como te llamas", "cual es tu nombre", "quien eres", "quien sos", "que eres", "que sos")) {
            "Soy Leo, tu asistente personal. Decime qué necesitás."
        } else null
    }
}
