package com.niko.assistant.ai

import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class GroqHttpResult(val code: Int, val body: String)

fun interface GroqTransport {
    suspend fun complete(apiKey: String, payload: JSONObject): GroqHttpResult
}

/** A single deadline covers retries. Search never falls back to a model without search. */
class GroqGateway(private val budgetMs: Long = 18_000L, private val transport: GroqTransport) {
    var lastError: String? = null
        private set
    var lastModelUsed: String? = null
        private set

    /** Retry only before any public text is emitted. Never restart an already spoken answer. */
    suspend fun executeStreaming(
        payload: JSONObject,
        apiKey: String,
        configuredModel: String,
        streaming: GroqStreamTransport,
        onDelta: suspend (String) -> Unit,
    ): NikoAiReply? {
        lastError = null
        lastModelUsed = null
        if (apiKey.isBlank() || apiKey.any { it.isWhitespace() || it.isISOControl() } || !GroqProtocol.isChatModel(configuredModel)) {
            lastError = "Revisá la clave y el modelo de conversación en Ajustes."
            return null
        }
        val text = StringBuilder()
        val outcome = withTimeoutOrNull(budgetMs) {
            for (model in GroqProtocol.models(configuredModel, false)) {
                val request = GroqConversation.forModel(payload, model, false).put("stream", true)
                val result = streaming.stream(apiKey, request) { delta ->
                    text.append(delta)
                    lastModelUsed = model
                    onDelta(delta)
                }
                if (result.completed && text.isNotBlank()) return@withTimeoutOrNull true
                lastError = GroqProtocol.describeError(result.code, result.body)
                if (text.isNotEmpty() || !GroqProtocol.canFallback(result.code, result.body)) break
            }
            false
        }
        if (outcome == true) { lastError = null; return NikoAiReply(text.toString().trim(), false, emptyList()) }
        if (outcome == null || text.isNotBlank()) lastError = "La respuesta se interrumpió. Revisá la conexión o volvé a intentarlo."
        if (text.isBlank()) return null
        val notice = " La respuesta se interrumpió; pedime que lo intente de nuevo."
        text.append(notice)
        onDelta(notice)
        return NikoAiReply(text.toString().trim(), false, emptyList())
    }

    suspend fun execute(payload: JSONObject, apiKey: String, configuredModel: String, useWeb: Boolean): NikoAiReply? {
        lastError = null
        lastModelUsed = null
        if (apiKey.isBlank()) { lastError = "Falta la API key de GroqCloud. Guardala en Ajustes."; return null }
        if (apiKey.any { it.isWhitespace() || it.isISOControl() }) { lastError = "La clave de GroqCloud contiene espacios o caracteres inválidos."; return null }
        if (!useWeb && !GroqProtocol.isChatModel(configuredModel)) {
            lastError = "Elegí un modelo de conversación de GroqCloud, por ejemplo ${GroqProtocol.DEFAULT_MODEL}."
            return null
        }
        var completed = false
        val answer = withTimeoutOrNull(budgetMs) {
            for (model in GroqProtocol.models(configuredModel, useWeb)) {
                val result = transport.complete(apiKey, GroqConversation.forModel(payload, model, useWeb))
                if (result.code in 200..299) {
                    val reply = runCatching { GroqProtocol.answer(JSONObject(result.body)) }.getOrNull()
                    if (reply != null) {
                        lastModelUsed = model
                        completed = true
                        return@withTimeoutOrNull reply
                    }
                    lastError = "GroqCloud no devolvió una respuesta utilizable. Reformulá la pregunta."
                    break
                }
                lastError = GroqProtocol.describeError(result.code, result.body)
                if (!GroqProtocol.canFallback(result.code, result.body)) break
            }
            completed = true
            null
        }
        if (answer != null) lastError = null
        else if (!completed) lastError = "GroqCloud tardó demasiado. Las funciones locales siguen disponibles."
        return answer
    }
}
