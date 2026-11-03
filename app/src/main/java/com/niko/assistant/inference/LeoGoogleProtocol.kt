package com.niko.assistant.inference

import com.niko.assistant.ai.ConversationTurn
import com.niko.assistant.modes.LeoMode
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** Native Gemini REST for personal standard/auth keys. Never puts a key in a URL. */
object LeoGoogleProtocol {
    fun endpoint(model: String): URI {
        require(model.matches(Regex("[a-zA-Z0-9._-]{1,160}"))) { "Nombre de modelo de Gemini inválido." }
        return URI("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
    }
    fun payload(model: String, question: String, evidence: String?, history: List<ConversationTurn>, mode: LeoMode): String {
        // Keep the same privacy, history selection and evidence contract as other providers.
        val chat = JSONObject(LeoInferenceProtocol.payload(model, question, evidence, history, mode, google = true))
        val messages = chat.getJSONArray("messages")
        val contents = JSONArray()
        val system = StringBuilder()
        for (index in 0 until messages.length()) {
            val message = messages.getJSONObject(index)
            val text = message.getString("content")
            if (message.getString("role") == "system") system.append(text)
            else contents.put(JSONObject().put("role", if (message.getString("role") == "assistant") "model" else "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text))))
        }
        val generation = JSONObject().put("maxOutputTokens", chat.optInt("max_tokens", 4096))
        LeoGoogleProvider.thinking(model, mode)?.let { generation.put("thinkingConfig", JSONObject().put("thinkingLevel", it)) }
        return JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.toString()))))
            .put("contents", contents).put("generationConfig", generation).toString()
    }
    fun answer(raw: String): String {
        require(raw.length <= 524288)
        val candidate = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
        require(candidate.optString("finishReason") == "STOP") { "Respuesta incompleta o bloqueada" }
        val parts = candidate.getJSONObject("content").getJSONArray("parts")
        val texts = mutableListOf<String>()
        for (index in 0 until parts.length()) {
            val part = parts.getJSONObject(index)
            require(!part.has("functionCall") && !part.has("executableCode") && !part.has("codeExecutionResult") && !part.has("inlineData"))
            if (!part.optBoolean("thought", false)) {
                val text = part.get("text")
                require(text is String)
                texts += text
            }
        }
        return texts.joinToString("").trim().also { require(it.isNotBlank() && it.length <= LeoInferenceProtocol.MAX_OUTPUT) }
    }
}
