package com.eddy.assistant.ai

import android.content.Context
import com.eddy.assistant.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class EddyWebSource(
    val title: String,
    val url: String,
)

data class EddyAiReply(
    val text: String,
    val webUsed: Boolean,
    val sources: List<EddyWebSource>,
)

class EddyAiClient(
    private val context: Context,
    private val baseUrlOverride: String? = null,
) {
    private fun resolvedBaseUrl(): String = baseUrlOverride
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
        ?: EddyAiSettings.baseUrl(context)

    val isConfigured: Boolean
        get() = resolvedBaseUrl().isNotBlank()

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = resolvedBaseUrl()
        if (baseUrl.isBlank()) return@withContext false
        val connection = runCatching {
            URL("${baseUrl.trimEnd('/')}/health").openConnection() as HttpURLConnection
        }.getOrNull() ?: return@withContext false
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "EDDY-Android/${BuildConfig.VERSION_NAME}")
            connection.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
    }

    suspend fun reply(
        message: String,
        memoryContext: String,
        forceWeb: Boolean = false,
    ): EddyAiReply? = withContext(Dispatchers.IO) {
        val baseUrl = resolvedBaseUrl()
        if (baseUrl.isBlank()) return@withContext null

        val endpoint = "${baseUrl.trimEnd('/')}/chat"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 75_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "EDDY-Android/${BuildConfig.VERSION_NAME}")
        }

        try {
            val payload = JSONObject()
                .put("message", message)
                .put("context", memoryContext)
                .put("force_web", forceWeb)
                .toString()

            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(payload) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) return@withContext null

            val json = JSONObject(body)
            val text = json.optString("reply").trim()
            if (text.isBlank()) return@withContext null

            val sourcesJson = json.optJSONArray("sources")
            val sources = buildList {
                if (sourcesJson != null) {
                    for (index in 0 until sourcesJson.length()) {
                        val item = sourcesJson.optJSONObject(index) ?: continue
                        val url = item.optString("url").trim()
                        if (url.isBlank()) continue
                        add(EddyWebSource(item.optString("title").trim().ifBlank { "Fuente web" }, url))
                    }
                }
            }

            EddyAiReply(
                text = text,
                webUsed = json.optBoolean("web_used", sources.isNotEmpty()),
                sources = sources,
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
