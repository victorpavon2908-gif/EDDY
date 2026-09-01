package com.eddy.assistant.ai

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

    suspend fun execute(payload: JSONObject, apiKey: String, configuredModel: String, useWeb: Boolean): EddyAiReply? {
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
                lastError = GroqProtocol.describeError(result.code)
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
