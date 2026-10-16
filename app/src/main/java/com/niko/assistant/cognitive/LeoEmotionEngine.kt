package com.niko.assistant.cognitive

import com.niko.assistant.ai.LeoExpression
import com.niko.assistant.ai.LeoPlayfulDialogue

/** Explicit acting and conversational boundaries. Emotional inference is deliberately separate. */
class LeoEmotionEngine(private val dialogue: LeoPlayfulDialogue) {
    data class Response(val text: String, val expression: LeoExpression?)
    fun reply(input: String): Response? = dialogue.reply(input)?.let {
        Response(it, LeoExpression.requested(input))
    }
}
