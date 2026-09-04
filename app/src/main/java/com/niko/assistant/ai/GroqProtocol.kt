package com.niko.assistant.ai

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

object GroqProtocol {
    // Fast chat is the normal voice path. The larger model remains a quality fallback.
    const val DEFAULT_MODEL = "llama-3.1-8b-instant"
    const val FAST_MODEL = DEFAULT_MODEL
    const val QUALITY_MODEL = "llama-3.3-70b-versatile"
    const val SEARCH_MODEL = "groq/compound"
    const val FAST_SEARCH_MODEL = "groq/compound-mini"

    fun isSearchModel(model: String) = model in setOf(SEARCH_MODEL, FAST_SEARCH_MODEL)
    fun isChatModel(model: String): Boolean =
        Regex("[a-zA-Z0-9][a-zA-Z0-9._-]*(?:/[a-zA-Z0-9][a-zA-Z0-9._-]*)?").matches(model) &&
            !model.contains("..") && !model.startsWith("groq/") &&
            listOf("gemini", "whisper", "embedding", "tts", "orpheus", "guard", "safeguard", "prompt-guard")
                .none { model.contains(it, ignoreCase = true) }

    fun models(configured: String, useWeb: Boolean): List<String> = if (useWeb) {
        // Compound Mini is intentionally first for voice latency; full Compound remains fallback.
        listOf(FAST_SEARCH_MODEL, SEARCH_MODEL)
    } else listOf(configured, FAST_MODEL, QUALITY_MODEL).distinct()

    private fun errorText(body: String): String = runCatching {
        val error = JSONObject(body).optJSONObject("error")
        listOf(error?.optString("code"), error?.optString("message")).joinToString(" ")
    }.getOrDefault(body).lowercase()

    /** A 403 can be model permission, which should fall back without blaming the API key. */
    fun isModelPermissionError(code: Int, body: String): Boolean {
        if (code != 403) return false
        val text = errorText(body)
        if (listOf("invalid_api_key", "authentication", "api key", "api_key").any(text::contains)) return false
        return text.contains("model") && listOf(
            "permission", "restricted", "blocked", "not permitted", "not allowed", "access", "terms",
        ).any(text::contains)
    }

    fun canFallback(code: Int, body: String): Boolean {
        if (code == 404 || code in setOf(500, 502, 503, 504) || isModelPermissionError(code, body)) return true
        val errorCode = runCatching { JSONObject(body).optJSONObject("error")?.optString("code") }.getOrNull()
        return code == 400 && errorCode in setOf("model_decommissioned", "model_not_found")
    }

    fun answer(json: JSONObject): NikoAiReply? {
        val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: return null
        if (choice.optString("finish_reason") !in setOf("", "stop", "length")) return null
        val message = choice.optJSONObject("message") ?: return null
        val text = (message.opt("content") as? String)?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Neither reasoning nor tool output is a final answer. Links in generated prose
        // alone are not evidence that a search actually ran.
        val sources = mutableListOf<NikoWebSource>()
        val tools = message.optJSONArray("executed_tools") ?: JSONArray()
        for (i in 0 until minOf(tools.length(), 10)) {
            val tool = tools.optJSONObject(i) ?: continue
            if (tool.optString("type") !in setOf("search", "web_search")) continue
            val search = tool.opt("search_results")
            val results = when (search) {
                is JSONObject -> search.optJSONArray("results")
                is JSONArray -> search
                else -> null
            } ?: continue
            for (j in 0 until minOf(results.length(), 30)) {
                val result = results.optJSONObject(j) ?: continue
                val url = result.optString("url").trim()
                val uri = runCatching { URI(url) }.getOrNull() ?: continue
                if (uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank() || uri.rawUserInfo != null) continue
                sources.add(NikoWebSource(result.optString("title").ifBlank { uri.host }.take(180), url))
            }
        }
        val unique = sources.distinctBy { it.url }.take(6)
        return NikoAiReply(text, unique.isNotEmpty(), unique)
    }

    fun describeError(code: Int, body: String = ""): String = when {
        code == 0 -> "No pude conectar con GroqCloud. Revisá la conexión a Internet."
        code == 400 -> "GroqCloud rechazó la solicitud. Revisá el modelo configurado."
        code == 401 -> "La API key de GroqCloud no es válida o ya fue revocada. Revisala en Ajustes."
        isModelPermissionError(code, body) -> "Ese modelo no está habilitado para esta clave de GroqCloud. Voy a probar otro modelo compatible."
        code == 403 -> "GroqCloud rechazó la autorización. Revisá los permisos de la clave en GroqCloud."
        code == 404 -> "El modelo no está disponible en GroqCloud para esta clave."
        code == 413 -> "La conversación es demasiado larga para esta solicitud de GroqCloud."
        code == 429 -> "Se alcanzó el límite de uso de GroqCloud. Esperá y volvé a intentarlo."
        else -> "GroqCloud no está disponible en este momento (HTTP $code)."
    }
}
