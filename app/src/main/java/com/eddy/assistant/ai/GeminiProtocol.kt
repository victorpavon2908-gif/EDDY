package com.eddy.assistant.ai

import org.json.JSONObject

/** Pure response/model policy, shared by diagnostics and conversation. */
internal object GeminiProtocol {
    fun normalizeModel(raw: String) = raw.trim().removePrefix("models/")
    fun isTextModel(raw: String): Boolean {
        val model = normalizeModel(raw)
        return Regex("gemini-[a-zA-Z0-9._-]+").matches(model) &&
            listOf("image", "embedding", "live", "tts", "audio", "robotics", "computer-use")
                .none { model.contains(it, ignoreCase = true) }
    }
    // A different model cannot repair an invalid key/request or an exhausted project quota.
    fun canFallback(code: Int) = code == 404 || code in setOf(500, 502, 503, 504)
    fun answer(json: JSONObject): EddyAiReply? {
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: return null
        if (candidate.optString("finishReason") !in setOf("", "STOP", "MAX_TOKENS")) return null
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return null
        val text = (0 until parts.length()).mapNotNull { index ->
            parts.optJSONObject(index)?.takeUnless { it.optBoolean("thought") }
                ?.optString("text")?.takeIf(String::isNotBlank)
        }.joinToString("\n").trim()
        if (text.isBlank()) return null
        val grounding = candidate.optJSONObject("groundingMetadata")
        val chunks = grounding?.optJSONArray("groundingChunks")
        val sources = if (chunks == null) emptyList() else (0 until chunks.length()).mapNotNull { index ->
            val web = chunks.optJSONObject(index)?.optJSONObject("web") ?: return@mapNotNull null
            val url = web.optString("uri")
            if (!url.startsWith("https://")) null else EddyWebSource(web.optString("title", "Fuente"), url)
        }.distinctBy { it.url }.take(6)
        return EddyAiReply(text, sources.isNotEmpty(), sources)
    }
}
