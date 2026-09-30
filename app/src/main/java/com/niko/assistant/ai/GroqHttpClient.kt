package com.niko.assistant.ai

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlinx.coroutines.channels.trySendBlocking

/** The key travels only in Authorization to the fixed Groq endpoint. */
class GroqHttpClient(
    private val openConnection: () -> HttpURLConnection = {
        URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
    },
) : GroqTransport, GroqStreamTransport {
    override suspend fun complete(apiKey: String, payload: JSONObject): GroqHttpResult = suspendCancellableCoroutine { continuation ->
        val connection = openConnection()
        val future = networkExecutor.submit {
            val result = try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5_000
                connection.readTimeout = 12_000
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val out = StringBuilder()
                    val buffer = CharArray(4_096)
                    while (out.length < 1_000_000) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        out.append(buffer, 0, count)
                    }
                    out.toString()
                }.orEmpty()
                GroqHttpResult(code, body)
            } catch (_: Exception) {
                GroqHttpResult(0, "")
            } finally { connection.disconnect() }
            if (continuation.isActive) continuation.resume(result)
        }
        continuation.invokeOnCancellation { future.cancel(true); connection.disconnect() }
    }

    private sealed interface Event {
        data class Delta(val text: String) : Event
        data class Result(val value: GroqStreamResult) : Event
    }

    override suspend fun stream(
        apiKey: String,
        payload: JSONObject,
        onDelta: suspend (String) -> Unit,
    ): GroqStreamResult {
        val events = kotlinx.coroutines.channels.Channel<Event>(32)
        val connection = openConnection()
        val future = networkExecutor.submit {
            val result = try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5_000
                connection.readTimeout = 8_000
                connection.useCaches = false
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("Accept", "text/event-stream")
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.doOutput = true
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val body = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                        val out = StringBuilder()
                        while (out.length < 16_384) {
                            val ch = reader.read()
                            if (ch < 0) break
                            out.append(ch.toChar())
                        }
                        out.toString() }.orEmpty()
                    GroqStreamResult(code, body)
                } else {
                    val complete = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        GroqSseReader.read(reader) { delta ->
                            // Backpressure bounds memory; cancellation closes the channel and unblocks this worker.
                            events.trySendBlocking(Event.Delta(delta)).getOrThrow()
                        }
                    }
                    GroqStreamResult(code, completed = complete)
                }
            } catch (_: Exception) {
                GroqStreamResult(0)
            } finally { connection.disconnect() }
            events.trySendBlocking(Event.Result(result))
            events.close()
        }
        try {
            for (event in events) when (event) {
                is Event.Delta -> onDelta(event.text)
                is Event.Result -> return event.value
            }
            return GroqStreamResult(0)
        } finally {
            events.cancel()
            future.cancel(true)
            connection.disconnect()
        }
    }

    private companion object {
        val networkExecutor = Executors.newFixedThreadPool(2) { task -> Thread(task, "NIKO-Groq").apply { isDaemon = true } }
    }
}
