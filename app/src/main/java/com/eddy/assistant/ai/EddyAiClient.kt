package com.eddy.assistant.ai

import com.eddy.assistant.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class EddyAiClient(
    private val baseUrl: String = BuildConfig.EDDY_AI_BASE_URL,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank()

    suspend fun reply(message: String, memoryContext: String): String? = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) return@withContext null

        val endpoint = "${baseUrl.trimEnd('/')}/chat"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "EDDY-Android/${BuildConfig.VERSION_NAME}")
        }

        try {
            val payload = JSONObject()
                .put("message", message)
                .put("context", memoryContext)
                .toString()

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write(payload)
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) return@withContext null

            JSONObject(body)
                .optString("reply")
                .trim()
                .takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
