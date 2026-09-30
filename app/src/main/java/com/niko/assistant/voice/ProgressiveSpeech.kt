package com.niko.assistant.voice

import java.util.ArrayDeque

/** Main-thread, single-turn phrase buffer. Clearing it makes interruption final. */
class ProgressiveSpeech {
    private val pending = StringBuilder()
    private val queue = ArrayDeque<String>()
    private var closed = false
    private var first = true

    fun append(delta: String) {
        if (closed) return
        pending.append(delta)
        drain(false)
    }

    fun finish() {
        if (closed) return
        drain(true)
        closed = true
    }

    val isDrained: Boolean get() = closed && queue.isEmpty()

    fun poll(): String? = queue.pollFirst()

    fun cancel() {
        closed = true
        pending.setLength(0)
        queue.clear()
    }

    private fun drain(final: Boolean) {
        while (pending.isNotEmpty()) {
            val text = pending.toString()
            val target = if (first) 100 else 220
            var end = -1
            // Wait for the next character, so a decimal or token-fragment is never mistaken for a sentence.
            for (i in 1 until text.length - 1) {
                if (text[i] in ".!?;:" && text[i + 1].isWhitespace()) {
                    val word = text.substring(0, i).substringAfterLast(' ').lowercase()
                    if (text[i] == '.' && (word in setOf("sr", "sra", "dr", "dra", "ud", "uds", "ej") || word.length == 1)) continue
                    end = i + 1
                    break
                }
            }
            if (end < 0 && text.length >= target) {
                end = text.indexOfLast { it.isWhitespace() }.takeIf { it >= target / 2 } ?: -1
            }
            if (end < 0) {
                if (!final) return
                end = text.length
            }
            val phrase = text.substring(0, end).trim()
            pending.delete(0, end)
            if (phrase.isNotEmpty()) { queue.addLast(phrase); first = false }
            else if (end == 0) return
        }
    }
}
