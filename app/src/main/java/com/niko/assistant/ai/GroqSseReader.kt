package com.niko.assistant.ai

import java.io.Reader
import org.json.JSONObject

/** Bounded SSE decoder. Only public answer content may reach speech, never reasoning/tools. */
object GroqSseReader {
    fun read(reader: Reader, onDelta: (String) -> Unit): Boolean {
        val line = StringBuilder()
        val event = StringBuilder()
        var bytesRead = 0
        var contentSize = 0
        var finished = false
        fun dispatch(): Boolean {
            val data = event.toString().trim()
            event.setLength(0)
            if (data.isEmpty()) return false
            if (data == "[DONE]") {
                check(finished) { "Stream ended without a completion reason" }
                return true
            }
            val json = JSONObject(data)
            check(!json.has("error")) { "Provider stream error" }
            val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return false
            val reason = choice.opt("finish_reason") as? String
            check(reason == null || reason in setOf("stop", "length")) { "Unusable completion" }
            if (reason != null) finished = true
            val delta = choice.optJSONObject("delta")?.opt("content") as? String
            if (!delta.isNullOrEmpty()) {
                contentSize += delta.length
                check(contentSize <= 16_000) { "Answer too long" }
                onDelta(delta)
            }
            return false
        }
        while (true) {
            val char = reader.read()
            if (char == -1) return false // EOF without [DONE] is an interrupted response.
            check(++bytesRead <= 1_000_000) { "Stream too large" }
            if (char == '\n'.code) {
                val value = line.toString().removeSuffix("\r")
                line.setLength(0)
                if (value.isEmpty()) {
                    if (dispatch()) return true
                } else if (value.startsWith("data:")) {
                    if (event.isNotEmpty()) event.append('\n')
                    event.append(value.removePrefix("data:").removePrefix(" "))
                    check(event.length <= 65_536) { "Event too large" }
                }
            } else {
                line.append(char.toChar())
                check(line.length <= 65_536) { "Line too large" }
            }
        }
    }
}
