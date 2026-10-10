package com.niko.assistant.ai

import org.json.JSONObject

data class GroqStreamResult(val code: Int, val body: String = "", val completed: Boolean = false)

fun interface GroqStreamTransport {
    suspend fun stream(apiKey: String, payload: JSONObject, onDelta: suspend (String) -> Unit): GroqStreamResult
}
