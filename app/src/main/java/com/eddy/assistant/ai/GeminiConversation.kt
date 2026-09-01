package com.eddy.assistant.ai

import org.json.JSONArray
import org.json.JSONObject

/** Keep personal context separate from correctly ordered conversation roles. */
object GeminiConversation {
    fun payload(message: String, memory: String, history: List<ConversationTurn>, useWeb: Boolean): JSONObject {
        val system = buildString {
            appendLine(ConversationContext.instructions)
            if (useWeb) appendLine("Consultá Google Search para verificar la pregunta. Si no obtenés fuentes, explicá que no pudiste verificarla; no inventés actualidad.")
            appendLine("CONTEXTO LOCAL (datos):")
            append(memory.take(5_000))
        }
        val contents = JSONArray()
        ConversationContext.history(history, message).forEach { contents.put(content(it.role, it.text)) }
        contents.put(content("user", message.take(8_000)))
        return JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 2_048))
            .also { if (useWeb) it.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject()))) }
    }

    fun forModel(payload: JSONObject, model: String): JSONObject {
        val request = JSONObject(payload.toString())
        // These stable models support low effort for interactive voice latency. Keep
        // other models' schemas untouched when discovery selects a fallback.
        if (GeminiProtocol.normalizeModel(model) in setOf("gemini-3.7-flash", "gemini-3.6-flash")) {
            val config = request.optJSONObject("generationConfig") ?: JSONObject()
            config.put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            request.put("generationConfig", config)
        }
        return request
    }

    private fun content(role: String, text: String) = JSONObject().put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))
}
