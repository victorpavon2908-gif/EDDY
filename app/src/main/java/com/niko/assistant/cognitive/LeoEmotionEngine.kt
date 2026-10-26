package com.niko.assistant.cognitive

import com.niko.assistant.ai.LeoExpression
import com.niko.assistant.ai.LeoPlayfulDialogue

/** Explicit acting, conversational boundaries, and session-scoped empathetic resonance. */
class LeoEmotionEngine(
    private val dialogue: LeoPlayfulDialogue,
    val heart: LeoEmotionalHeart = LeoEmotionalHeart(),
) {
    data class Response(val text: String, val expression: LeoExpression?)
    fun reply(input: String): Response? = dialogue.reply(input)?.let {
        Response(it, LeoExpression.requested(input))
    } ?: heart.directReply(input)?.let {
        Response(it.standaloneReply, it.expression)
    }
}
