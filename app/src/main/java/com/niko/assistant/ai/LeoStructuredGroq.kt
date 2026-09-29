package com.niko.assistant.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Adaptador Groq dedicado exclusivamente a compilar intención -> DSL.
 * No habilita herramientas web y nunca ejecuta el texto devuelto por el modelo.
 */
class LeoStructuredGroq(
    context: Context,
    private val transport: GroqTransport = GroqHttpClient(),
) {
    private val appContext = context.applicationContext

    @Volatile var lastError: String? = null
        private set

    val isConfigured: Boolean get() = NikoAiSettings.apiKey(appContext).isNotBlank()

    suspend fun complete(instruction: String): String? {
        lastError = null
        if (instruction.isBlank()) {
            lastError = "La instrucción semántica está vacía."
            return null
        }
        val apiKey = NikoAiSettings.apiKey(appContext)
        if (apiKey.isBlank()) {
            lastError = "Falta la API key de GroqCloud. Las órdenes locales siguen disponibles."
            return null
        }
        if (apiKey.any { it.isWhitespace() || it.isISOControl() }) {
            lastError = "La clave de GroqCloud contiene caracteres inválidos."
            return null
        }
        if (!hasInternet()) {
            lastError = "Sin Internet. LEO seguirá resolviendo órdenes con el cerebro local."
            return null
        }

        val configured = NikoAiSettings.model(appContext)
        val models = listOf(GroqProtocol.FAST_MODEL, configured, GroqProtocol.DEFAULT_MODEL)
            .filter(GroqProtocol::isChatModel)
            .distinct()

        var completed = false
        val answer = withTimeoutOrNull(BUDGET_MS) {
            for (model in models) {
                val result = transport.complete(apiKey, payload(model, instruction))
                if (result.code in 200..299) {
                    completed = true
                    val content = parseStrictContent(result.body)
                    if (content == null) lastError = "GroqCloud devolvió un plan semántico no utilizable."
                    return@withTimeoutOrNull content
                }
                lastError = GroqProtocol.describeError(result.code)
                if (!GroqProtocol.canFallback(result.code, result.body)) {
                    completed = true
                    return@withTimeoutOrNull null
                }
            }
            completed = true
            null
        }
        if (answer != null) lastError = null
        else if (!completed) lastError = "GroqCloud tardó demasiado. LEO mantuvo disponibles las órdenes locales."
        return answer
    }

    private fun payload(model: String, instruction: String): JSONObject = JSONObject()
        .put("model", model)
        .put("temperature", 0)
        .put("max_completion_tokens", MAX_COMPLETION_TOKENS)
        .put("stream", false)
        .put(
            "messages",
            JSONArray()
                .put(
                    JSONObject()
                        .put("role", "system")
                        .put(
                            "content",
                            "Sos un compilador de acciones para Android. Devolvé exclusivamente el DSL solicitado por el usuario, sin Markdown, explicación ni texto adicional.",
                        ),
                )
                .put(JSONObject().put("role", "user").put("content", instruction.take(MAX_PROMPT_CHARS))),
        )

    private fun parseStrictContent(body: String): String? = runCatching {
        val root = JSONObject(body)
        val choice = root.optJSONArray("choices")?.optJSONObject(0) ?: return@runCatching null
        val finish = choice.optString("finish_reason")
        if (finish !in setOf("", "stop")) return@runCatching null
        val content = choice.optJSONObject("message")?.optString("content")?.trim().orEmpty()
        content.takeIf { it.isNotBlank() && it.length <= MAX_OUTPUT_CHARS }
    }.getOrNull()

    private fun hasInternet(): Boolean {
        val manager = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val BUDGET_MS = 6_000L
        const val MAX_COMPLETION_TOKENS = 360
        const val MAX_PROMPT_CHARS = 6_000
        const val MAX_OUTPUT_CHARS = 4_000
    }
}
