package com.niko.assistant.inference

import kotlinx.coroutines.suspendCancellableCoroutine
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
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<kotlinx.coroutines.CancellableContinuation<String?>>()
    private val workers = ThreadPoolExecutor(2, 2, 10, TimeUnit.SECONDS, ArrayBlockingQueue(2))
    suspend fun reply(endpoint: String, model: String, key: String, question: String): String? {
        if (!LeoInferenceProtocol.maySend(question)) return null
        val uri = runCatching { LeoInferenceProtocol.endpoint(endpoint) }.getOrNull() ?: return null
        return suspendCancellableCoroutine { continuation ->
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
                    connection.readTimeout = 45000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    if (key.isNotBlank()) {
                        require(key.length <= 4096 && key.none { it.isISOControl() })
                        connection.setRequestProperty("Authorization", "Bearer $key")
                    }
                    val payload = LeoInferenceProtocol.payload(model, question).toByteArray(Charsets.UTF_8)
                    connection.setFixedLengthStreamingMode(payload.size)
                    connection.outputStream.use { it.write(payload) }
                    check(connection.responseCode == 200)
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
                    LeoInferenceProtocol.answer(String(bytes, Charsets.UTF_8))
                }.getOrNull()
                runCatching { active.getAndSet(null)?.disconnect() }
                pending.remove(continuation)
                if (continuation.isActive) continuation.resume(result)
            } }.getOrNull()
            if (future == null) { pending.remove(continuation); continuation.resume(null) }
            continuation.invokeOnCancellation {
                pending.remove(continuation)
                runCatching { active.getAndSet(null)?.disconnect() }
                future?.cancel(true)
            }
        }
    }
    override fun close() {
        workers.shutdownNow()
        pending.forEach { it.cancel(kotlinx.coroutines.CancellationException("Inference client closed")) }
        pending.clear()
    }
}
