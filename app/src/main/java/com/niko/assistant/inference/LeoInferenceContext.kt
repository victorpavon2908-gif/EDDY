package com.niko.assistant.inference

import com.niko.assistant.ai.ConversationTurn

/** Only complete recent exchanges. Never export system/tool roles or cross a private turn. */
object LeoInferenceContext {
    const val MAX_CHARS = 6000
    fun select(history: List<ConversationTurn>): List<ConversationTurn> {
        val result = mutableListOf<ConversationTurn>()
        var size = 0
        var index = history.lastIndex
        repeat(3) {
            if (index < 1) return result
            val answer = history[index]
            val question = history[index - 1]
            if (question.role != "user" || answer.role !in setOf("assistant", "model") ||
                !LeoInferenceProtocol.maySend(question.text) || !LeoInferenceProtocol.maySend(answer.text) ||
                question.text.length > 2000 || answer.text.length > 2000 ||
                size + question.text.length + answer.text.length > MAX_CHARS) return result
            result.addAll(0, listOf(question, answer.copy(role = "assistant")))
            size += question.text.length + answer.text.length
            index -= 2
        }
        return result
    }
}
