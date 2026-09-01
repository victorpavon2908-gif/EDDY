package com.eddy.assistant.ai

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject

/** The key travels only in Authorization to the fixed Groq endpoint. */
class GroqHttpClient : GroqTransport {
    override suspend fun complete(apiKey: String, payload: JSONObject): GroqHttpResult = suspendCancellableCoroutine { continuation ->
        val connection = URL("https://api.groq.com/openai/v1/chat/completions").openConnection() as HttpURLConnection
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

    private companion object {
        val networkExecutor = Executors.newFixedThreadPool(2) { task -> Thread(task, "EDDY-Groq").apply { isDaemon = true } }
    }
}
