package com.niko.assistant.inference

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** Bounded I/O, cancellation disconnects the request; failures leave local routes available. */
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
    data class Result(val answer: String? = null, val failure: Failure? = null)
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<kotlinx.coroutines.CancellableContinuation<Result>>()
    private val workers = ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, ArrayBlockingQueue(2))
    suspend fun reply(endpoint: String, model: String, key: String, question: String, evidence: String? = null, history: List<com.niko.assistant.ai.ConversationTurn> = emptyList(), mode: com.niko.assistant.modes.LeoMode = com.niko.assistant.modes.LeoMode.NORMAL, timeoutMs: Long = 30_000L): String? {
        return request(endpoint, model, key, question, evidence, history, mode, timeoutMs).answer
    }
    suspend fun request(endpoint: String, model: String, key: String, question: String, evidence: String? = null, history: List<com.niko.assistant.ai.ConversationTurn> = emptyList(), mode: com.niko.assistant.modes.LeoMode = com.niko.assistant.modes.LeoMode.NORMAL, timeoutMs: Long = 30_000L): Result {
        if (!LeoInferenceProtocol.maySend(question)) return Result(failure = Failure.INVALID)
        val uri = runCatching { LeoInferenceProtocol.endpoint(endpoint) }.getOrNull() ?: return Result(failure = Failure.INVALID)
        require(timeoutMs > 0)
        return withTimeoutOrNull(com.niko.assistant.agent.LeoTurnBudget.limit(minOf(timeoutMs, mode.inferenceTimeoutMs))) {
            suspendCancellableCoroutine { continuation ->
                pending.add(continuation)
                val active = AtomicReference<HttpURLConnection?>()
                val future = runCatching { workers.submit {
                    val result = runCatching {
                        check(continuation.isActive)
                        val connection = connect(uri)
                        active.set(connection)
                        check(continuation.isActive)
                        connection.instanceFollowRedirects = false
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 12000
                        connection.readTimeout = if (mode == com.niko.assistant.modes.LeoMode.SUPER) 15000 else 45000
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        if (key.isNotBlank()) {
                            require(key.length <= 4096 && key.none { it.isISOControl() })
                            connection.setRequestProperty("Authorization", "Bearer $key")
                        }
                        val payload = LeoInferenceProtocol.payload(model, question, evidence, history, mode,
                            google = LeoGoogleProvider.isGoogle(endpoint)).toByteArray(Charsets.UTF_8)
                        connection.setFixedLengthStreamingMode(payload.size)
                        connection.outputStream.use { it.write(payload) }
                        val failure = when (connection.responseCode) {
                            200 -> null
                            401, 403 -> Failure.CREDENTIAL
                            404 -> Failure.MODEL
                            429 -> Failure.QUOTA
                            in 500..599 -> Failure.PROVIDER
                            else -> Failure.INVALID
                        }
                        if (failure != null) return@runCatching Result(failure = failure)
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                check(continuation.isActive)
                                val read = input.read(buffer)
                                if (read < 0) break
                                require(output.size() + read <= 524288)
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        require(bytes.size <= 524288)
                        runCatching { Result(answer = LeoInferenceProtocol.answer(String(bytes, Charsets.UTF_8))) }
                            .getOrElse { Result(failure = Failure.RESPONSE) }
                    }.getOrElse { Result(failure = Failure.CONNECTION) }
                    runCatching { active.getAndSet(null)?.disconnect() }
                    pending.remove(continuation)
                    if (continuation.isActive) continuation.resume(result)
                } }.getOrNull()
                if (future == null) { pending.remove(continuation); continuation.resume(Result(failure = Failure.PROVIDER)) }
                continuation.invokeOnCancellation {
                    pending.remove(continuation)
                    runCatching { active.getAndSet(null)?.disconnect() }
                    future?.cancel(true)
                }
            }
        } ?: Result(failure = Failure.TIMEOUT)
    }
    override fun close() {
        workers.shutdownNow()
        pending.forEach { it.cancel(kotlinx.coroutines.CancellationException("Inference client closed")) }
        pending.clear()
    }
}
