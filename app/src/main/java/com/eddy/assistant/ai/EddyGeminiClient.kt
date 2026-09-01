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

    @Volatile
    var lastModelUsed: String? = null
        private set

    val isConfigured: Boolean get() = EddyAiSettings.apiKey(appContext).isNotBlank()

    /**
     * Discovers the models that Google exposes to this exact API key/project and
     * tests the configured model first, then every compatible text model until one answers.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        lastError = null
        lastModelUsed = null
        val key = EddyAiSettings.apiKey(appContext)
        if (key.isBlank()) {
            lastError = "Falta la API key de Gemini."
            return@withContext false
        }

        val payload = JSONObject().put(
            "contents",
            JSONArray().put(
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", "Respondé únicamente con OK.")),
                ),
            ),
        )
        executeWithFallback(key, payload)?.isNotBlank() == true
    }

    suspend fun reply(message: String, memoryContext: String): String? = withContext(Dispatchers.IO) {
        lastError = null
        lastModelUsed = null
        val key = EddyAiSettings.apiKey(appContext)
        if (key.isBlank()) {
            lastError = "Falta la API key de Gemini."
            return@withContext null
        }
        if (message.isBlank()) {
            lastError = "El mensaje está vacío."
            return@withContext null
        }

        val system = """
            Sos EDDY, un asistente personal nicaragüense integrado en un teléfono Android.
            Hablá de forma natural, cercana y breve, usando voseo sin caricaturizar el acento.
            Mantené el hilo de la conversación usando la memoria local que recibís abajo.
            Si el usuario está hablando de forma casual, respondé conversacionalmente; no conviertas todo en comandos.
            No afirmés que ejecutaste una acción del teléfono si la app no te confirmó que se ejecutó.
            MEMORIA LOCAL DE DIÁLOGO:
            ${memoryContext.takeLast(12_000)}
        """.trimIndent()

        // Keep the request deliberately conservative so it works across Gemini generations.
        // Model-specific sampling/thinking options are avoided in the fallback path.
        val payload = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", message.trim())))
            ))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 700))

        executeWithFallback(key, payload)
    }

    private fun executeWithFallback(key: String, payload: JSONObject): String? {
        val configured = normalizeModel(EddyAiSettings.model(appContext))
        val cached = cachedWorkingModel()
        val discovered = discoverGenerateContentModels(key)

        val candidates = linkedSetOf<String>().apply {
            if (cached.isNotBlank()) add(cached)
            if (configured.isNotBlank()) add(configured)
            discovered.sortedWith(compareBy<String> { modelPriority(it) }.thenBy { it }).forEach(::add)
            // Safe stable fallbacks in case models.list is temporarily unavailable.
            add("gemini-3.7-flash")
            add("gemini-3.6-flash")
            add("gemini-3.5-flash")
            add("gemini-3.5-flash-lite")
            add("gemini-3.1-flash-lite")
            add("gemini-2.5-flash")
            add("gemini-2.5-flash-lite")
        }.filter(::isConversationalTextModel)

        if (candidates.isEmpty()) {
            lastError = "Gemini no devolvió ningún modelo de texto compatible con generateContent."
            return null
        }

        val failures = mutableListOf<String>()
        for (model in candidates) {
            val result = execute(model, key, payload)
            if (result.text != null) {
                lastModelUsed = model
                saveWorkingModel(model)
                lastError = null
                return result.text
            }

            failures += "$model: ${result.message.take(120)}"
            if (!result.retryWithAnotherModel) {
                lastError = result.message
                return null
            }
        }

        lastError = "Ningún modelo disponible respondió. " + failures.takeLast(4).joinToString(" | ")
        return null
    }

    /** GET /v1beta/models and keep only models that advertise generateContent. */
    private fun discoverGenerateContentModels(key: String): List<String> {
        val connection = runCatching {
            URL("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000")
                .openConnection() as HttpURLConnection
        }.getOrNull() ?: return emptyList()

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("x-goog-api-key", key)
            if (connection.responseCode !in 200..299) return emptyList()

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val models = JSONObject(body).optJSONArray("models") ?: return emptyList()
            buildList {
                for (i in 0 until models.length()) {
                    val item = models.optJSONObject(i) ?: continue
                    val methods = item.optJSONArray("supportedGenerationMethods")
                    var supportsGenerateContent = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j).equals("generateContent", ignoreCase = true)) {
                                supportsGenerateContent = true
                                break
                            }
                        }
                    }
                    if (!supportsGenerateContent) continue
                    val name = normalizeModel(item.optString("name"))
                    if (isConversationalTextModel(name)) add(name)
                }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection.disconnect()
        }
    }

    private fun execute(model: String, key: String, payload: JSONObject): AttemptResult {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val connection = runCatching { URL(endpoint).openConnection() as HttpURLConnection }
            .getOrElse {
                return AttemptResult(null, "No pude abrir la conexión con Gemini: ${it.message.orEmpty()}", true)
            }

        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 35_000
            connection.doOutput = true
            connection.useCaches = false
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("x-goog-api-key", key)
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val raw = runCatching {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                }.getOrNull().orEmpty()
                val apiMessage = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                val message = when (code) {
                    400 -> "HTTP 400 en '$model': ${apiMessage.ifBlank { "Solicitud no compatible con este modelo." }}"
                    401 -> "HTTP 401: ${apiMessage.ifBlank { "La clave no fue autenticada." }}"
                    403 -> "HTTP 403: ${apiMessage.ifBlank { "La clave/proyecto no tiene permiso para usar Gemini API." }}"
                    404 -> "HTTP 404: el modelo '$model' no está disponible para esta clave/proyecto. ${apiMessage}".trim()
                    429 -> "HTTP 429 en '$model': ${apiMessage.ifBlank { "Se alcanzó el límite/cuota de Gemini." }}"
                    500, 502, 503, 504 -> "HTTP $code en '$model': ${apiMessage.ifBlank { "Gemini está temporalmente no disponible." }}"
                    else -> "HTTP $code en '$model': ${apiMessage.ifBlank { raw.take(300) }}"
                }
                // Authentication/authorization errors apply to the key/project, so trying more models is pointless.
                val retry = code !in setOf(401, 403)
                return AttemptResult(null, message, retry)
            }

            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val parts = json.optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: return AttemptResult(null, "'$model' respondió HTTP 200 pero sin texto utilizable.", true)

            val text = buildString {
                for (i in 0 until parts.length()) {
                    parts.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }?.let(::append)
                }
            }.trim()

            return if (text.isNotBlank()) {
                AttemptResult(text, "", false)
            } else {
                AttemptResult(null, "'$model' respondió vacío.", true)
            }
        } catch (e: Exception) {
            return AttemptResult(null, "Error de red/TLS con '$model': ${e.javaClass.simpleName}: ${e.message.orEmpty()}", true)
        } finally {
            connection.disconnect()
        }
    }

    private fun normalizeModel(raw: String): String = raw.trim().removePrefix("models/")

    /** Exclude image/audio/live/embedding/specialized endpoints from normal EDDY chat. */
    private fun isConversationalTextModel(model: String): Boolean {
        if (!model.startsWith("gemini-", ignoreCase = true)) return false
        val blocked = listOf(
            "image", "embedding", "live", "tts", "transcribe", "robotics", "computer-use"
        )
        return blocked.none { model.contains(it, ignoreCase = true) }
    }

    private fun modelPriority(model: String): Int = when {
        model == "gemini-3.7-flash" -> 0
        model == "gemini-3.6-flash" -> 1
        model == "gemini-3.5-flash" -> 2
        model.contains("flash-lite") -> 3
        model.contains("flash") -> 4
        model.contains("pro") -> 5
        else -> 10
    }

    private fun cachedWorkingModel(): String =
        appContext.getSharedPreferences(PREFS_RUNTIME, Context.MODE_PRIVATE)
            .getString(KEY_WORKING_MODEL, "")
            .orEmpty()
            .let(::normalizeModel)

    private fun saveWorkingModel(model: String) {
        appContext.getSharedPreferences(PREFS_RUNTIME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_WORKING_MODEL, normalizeModel(model))
            .apply()
    }

    private data class AttemptResult(
        val text: String?,
        val message: String,
        val retryWithAnotherModel: Boolean,
    )

    private companion object {
        const val PREFS_RUNTIME = "eddy_gemini_runtime"
        const val KEY_WORKING_MODEL = "last_working_model"
    }
}
