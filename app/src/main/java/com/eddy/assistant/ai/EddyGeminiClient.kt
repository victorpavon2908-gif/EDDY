package com.eddy.assistant.ai

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Direct Gemini connection: EDDY -> Google Gemini, with no EDDY/Render backend hop. */
class EddyGeminiClient(context: Context) {
    private val appContext = context.applicationContext

    @Volatile
    var lastError: String? = null
        private set

    val isConfigured: Boolean get() = EddyAiSettings.apiKey(appContext).isNotBlank()

    suspend fun testConnection(): Boolean =
        reply("Respondé únicamente con OK.", "Prueba local de conexión de EDDY.")?.isNotBlank() == true

    suspend fun reply(message: String, memoryContext: String): String? = withContext(Dispatchers.IO) {
        lastError = null
        val key = EddyAiSettings.apiKey(appContext)
        if (key.isBlank()) {
            lastError = "Falta la API key de Gemini."
            return@withContext null
        }
        if (message.isBlank()) {
            lastError = "El mensaje está vacío."
            return@withContext null
        }

        val model = EddyAiSettings.model(appContext)
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val system = """
            Sos EDDY, un asistente personal nicaragüense integrado en un teléfono Android.
            Hablá de forma natural, cercana y breve, usando voseo sin caricaturizar el acento.
            Mantené el hilo de la conversación usando la memoria local que recibís abajo.
            Si el usuario está hablando de forma casual, respondé conversacionalmente; no conviertas todo en comandos.
            No afirmés que ejecutaste una acción del teléfono si la app no te confirmó que se ejecutó.
            MEMORIA LOCAL DE DIÁLOGO:
            ${memoryContext.takeLast(12_000)}
        """.trimIndent()

        // Gemini 3.7 no admite los parámetros de muestreo heredados (temperature/top_p/top_k).
        val payload = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", message.trim())))
            ))
            .put("generationConfig", JSONObject()
                .put("maxOutputTokens", 700)
                .put("thinkingConfig", JSONObject().put("thinkingLevel", "low"))
            )

        val connection = runCatching { URL(endpoint).openConnection() as HttpURLConnection }
            .getOrElse {
                lastError = "No pude abrir la conexión con Gemini: ${it.message.orEmpty()}"
                return@withContext null
            }
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("x-goog-api-key", key)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val raw = runCatching { connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }.getOrNull().orEmpty()
                val apiMessage = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                lastError = when (code) {
                    400 -> "Gemini rechazó la solicitud (400): ${apiMessage.ifBlank { "revisá el modelo o la configuración." }}"
                    401, 403 -> "Gemini rechazó la API key ($code): ${apiMessage.ifBlank { "revisá que la clave esté activa y autorizada." }}"
                    404 -> "El modelo '$model' no está disponible para esta clave (404)."
                    429 -> "Gemini alcanzó el límite de uso de esta clave (429)."
                    else -> "Gemini respondió HTTP $code: ${apiMessage.ifBlank { raw.take(220) }}"
                }
                return@withContext null
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val parts = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
            if (parts == null) {
                lastError = "Gemini respondió sin texto utilizable."
                return@withContext null
            }
            buildString {
                for (i in 0 until parts.length()) {
                    parts.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }?.let(::append)
                }
            }.trim().takeIf { it.isNotBlank() } ?: run {
                lastError = "Gemini respondió vacío."
                null
            }
        } catch (e: Exception) {
            lastError = "Error de conexión con Gemini: ${e.message.orEmpty()}"
            null
        } finally {
            connection.disconnect()
        }
    }
}
