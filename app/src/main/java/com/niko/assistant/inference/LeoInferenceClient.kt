package com.niko.assistant.inference

import com.niko.assistant.ai.LeoWebRequests
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URI

/** Bounded I/O; a blocked socket or disconnect cannot hold the caller past its deadline. */
class LeoInferenceClient(
    private val connect: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
) : Closeable {
    enum class Failure(val description: String) {
        INVALID("Revisá la dirección, el modelo y la clave."),
        CREDENTIAL("La clave no es válida o el proyecto no tiene acceso. Revisá la clave y los permisos del proveedor."),
        MODEL("El modelo no está disponible para este proyecto. Revisá su nombre y acceso."),
        QUOTA("Se alcanzó la cuota o el límite de solicitudes. Revisá el uso con tu proveedor y reintentá más tarde."),
        PROVIDER("El proveedor no está disponible. Podés reintentar; las funciones locales siguen disponibles."),
        RESPONSE("El proveedor no devolvió una respuesta completa y válida."),
        CONNECTION("No pude conectar con el proveedor. Revisá Internet e intentá de nuevo."),
        TIMEOUT("El proveedor tardó demasiado. Podés reintentar."),
    }
    data class Result(val answer: String? = null, val failure: Failure? = null, val httpStatus: Int? = null) {
        fun description(): String = (failure?.description ?: "No se obtuvo una respuesta válida.") +
            (httpStatus?.let { " (HTTP $it)" } ?: "")
    }
    private val requests = LeoWebRequests(workerCount = 2, queueSize = 2)
    suspend fun reply(endpoint: String, model: String, key: String, question: String, evidence: String? = null, history: List<com.niko.assistant.ai.ConversationTurn> = emptyList(), mode: com.niko.assistant.modes.LeoMode = com.niko.assistant.modes.LeoMode.NORMAL, timeoutMs: Long = 30_000L): String? =
        request(endpoint, model, key, question, evidence, history, mode, timeoutMs).answer

    suspend fun request(endpoint: String, model: String, key: String, question: String, evidence: String? = null, history: List<com.niko.assistant.ai.ConversationTurn> = emptyList(), mode: com.niko.assistant.modes.LeoMode = com.niko.assistant.modes.LeoMode.NORMAL, timeoutMs: Long = 30_000L): Result {
        if (!LeoInferenceProtocol.maySend(question)) return Result(failure = Failure.INVALID)
        val uri = runCatching { LeoInferenceProtocol.endpoint(endpoint) }.getOrNull() ?: return Result(failure = Failure.INVALID)
        val google = LeoGoogleProvider.isGoogle(endpoint)
        val target = if (google) runCatching { LeoGoogleProtocol.endpoint(model) }.getOrNull()
            ?: return Result(failure = Failure.MODEL) else uri
        require(timeoutMs > 0)
        val budgetMs = com.niko.assistant.agent.LeoTurnBudget.limit(minOf(timeoutMs, mode.inferenceTimeoutMs))
        val start = System.nanoTime()
        return withTimeoutOrNull(budgetMs) {
            suspend fun send(): Result {
            var busy = false
            return requests.run(onBusy = { busy = true }) { track ->
                try {
                    check(!Thread.currentThread().isInterrupted)
                    val connection = connect(target)
                    track(connection)
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 12000
                    connection.readTimeout = if (mode == com.niko.assistant.modes.LeoMode.SUPER) 15000 else 25000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", if (google) "application/json" else "application/json; charset=utf-8")
                    if (google) {
                        connection.setRequestProperty("User-Agent", "LEO/Android")
                        connection.setRequestProperty("Connection", "close")
                    }
                    if (key.isNotBlank()) {
                        require(key.length <= 4096 && key.none { it.isISOControl() })
                        connection.setRequestProperty(if (google) "x-goog-api-key" else "Authorization", if (google) key else "Bearer $key")
                    }
                    val payload = (if (google) LeoGoogleProtocol.payload(model, question, evidence, history, mode)
                        else LeoInferenceProtocol.payload(model, question, evidence, history, mode)).toByteArray(Charsets.UTF_8)
                    if (!google) connection.setFixedLengthStreamingMode(payload.size)
                    check(!Thread.currentThread().isInterrupted)
                    connection.outputStream.use { it.write(payload) }
                    val status = connection.responseCode
                    val failure = when (status) {
                        200 -> null
                        401, 403 -> Failure.CREDENTIAL
                        404 -> Failure.MODEL
                        429 -> Failure.QUOTA
                        in 500..599 -> Failure.PROVIDER
                        else -> Failure.INVALID
                    }
                    if (failure != null) return@run Result(failure = failure, httpStatus = status)
                    val bytes = connection.inputStream.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            check(!Thread.currentThread().isInterrupted)
                            val read = input.read(buffer)
                            if (read < 0) break
                            require(output.size() + read <= 524288)
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                    runCatching { Result(answer = if (google) LeoGoogleProtocol.answer(String(bytes, Charsets.UTF_8))
                        else LeoInferenceProtocol.answer(String(bytes, Charsets.UTF_8))) }
                        .getOrElse { Result(failure = Failure.RESPONSE) }
                } catch (_: java.net.SocketTimeoutException) { Result(failure = Failure.TIMEOUT) }
                catch (_: Exception) { Result(failure = Failure.CONNECTION) }
            } ?: Result(failure = if (busy) Failure.PROVIDER else Failure.CONNECTION)
            }
            var result = send()
            val remaining = budgetMs - (System.nanoTime() - start) / 1_000_000L
            if (google && result.httpStatus in setOf(502, 503, 504) && remaining >= 5_000L) {
                kotlinx.coroutines.delay(300)
                result = send()
            }
            result
        } ?: Result(failure = Failure.TIMEOUT)
    }
    override fun close() { requests.close() }
}
