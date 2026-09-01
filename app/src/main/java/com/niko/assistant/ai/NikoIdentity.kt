package com.niko.assistant.ai

import com.niko.assistant.memory.MemoryLearning

/** Identity questions stay local and cannot be overwritten by an old learned answer. */
object NikoIdentity {
    /** Avoid spelling the all-caps brand as separate letters in either speech engine. */
    fun forSpeech(text: String): String = text.replace(Regex("(?i)\\bniko\\b"), "Nico")

    fun replyTo(input: String): String? {
        val question = MemoryLearning.key(input).removePrefix("niko ").removePrefix("nico ")
        return if (question in setOf("como te llamas", "cual es tu nombre", "quien eres", "quien sos", "que eres", "que sos")) {
            "Soy Niko, tu asistente personal. Decime qué necesitás."
        } else null
    }
}
