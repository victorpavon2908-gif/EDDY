package com.eddy.assistant.ai

import android.content.Context
import com.eddy.assistant.BuildConfig
import com.eddy.assistant.actions.ActionExecutor
import com.eddy.assistant.actions.ActionResult
import com.eddy.assistant.brain.SystemPanel
import com.eddy.assistant.devicecontrol.EddyAccessibilityService
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class EddyWebSource(val title: String, val url: String)

data class EddyAiReply(
    val text: String,
    val webUsed: Boolean,
    val sources: List<EddyWebSource>,
    val evidence: String = "",
)

/**
 * Cliente local-first de EDDY.
 *
 * 1) Reutiliza conocimiento local cuando corresponde.
 * 2) Envía lenguaje desconocido al planificador remoto.
 * 3) Ejecuta acciones en Android, con confirmación para planes sensibles.
 * 4) Las búsquedas web usan el endpoint de investigación avanzada y regresan una respuesta hablable.
 */
class EddyAiClient(
    private val context: Context,
    private val baseUrlOverride: String? = null,
) {
    private val appContext = context.applicationContext
    private val executor by lazy { ActionExecutor(appContext) }
    private val knowledgePrefs by lazy {
        appContext.getSharedPreferences(KNOWLEDGE_PREFS, Context.MODE_PRIVATE)
    }

    @Volatile private var pendingActions: JSONArray? = null
    @Volatile private var pendingAt: Long = 0L

    private fun resolvedBaseUrl(): String = baseUrlOverride
        ?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        ?: EddyAiSettings.baseUrl(context)

    val isConfigured: Boolean get() = resolvedBaseUrl().isNotBlank()

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val base = resolvedBaseUrl()
        if (base.isBlank()) return@withContext false
        val connection = runCatching { URL("$base/health").openConnection() as HttpURLConnection }.getOrNull()
            ?: return@withContext false
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 1_500
            connection.readTimeout = 2_500
            connection.setRequestProperty("Accept", "application/json")
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
    ): EddyAiReply? {
        val cleaned = message.trim()
        if (cleaned.isBlank()) return null

        if (forceWeb) return requestWebSearch(cleaned, memoryContext)

        consumePendingConfirmation(cleaned)?.let { return it }
        findLearnedReply(cleaned)?.let { return it }

        val planJson = requestPlan(cleaned, memoryContext) ?: return null
        val actions = planJson.optJSONArray("actions") ?: JSONArray()
        val modelReply = planJson.optString("reply").trim()
        val needsConfirmation = planJson.optBoolean("needs_confirmation", false)

        if (needsConfirmation && actions.length() > 0) {
            pendingActions = JSONArray(actions.toString())
            pendingAt = System.currentTimeMillis()
            val text = modelReply.ifBlank { "Esa acción necesita confirmación." }
            return EddyAiReply("$text Decime sí para hacerla o no para cancelarla.", false, emptyList())
        }

        val webQueries = extractWebQueries(actions)
        val directActions = withoutWebActions(actions)
        val directMessages = executePlannedActions(directActions)

        var webReply: EddyAiReply? = null
        if (webQueries.isNotEmpty()) {
            webReply = requestWebSearch(webQueries.joinToString(" "), memoryContext)
        }

        val finalText = when {
            webReply != null -> webReply.text
            modelReply.isNotBlank() -> modelReply
            directMessages.isNotEmpty() -> directMessages.joinToString(" ")
            else -> "Aquí estoy."
        }
        val reply = EddyAiReply(
            text = finalText,
            webUsed = webReply?.webUsed == true,
            sources = webReply?.sources.orEmpty(),
            evidence = webReply?.evidence.orEmpty(),
        )

        // Nunca cachear una petición que ejecutó acciones: repetirla debe volver a ejecutarlas.
        if (actions.length() == 0 && !reply.webUsed) rememberLearnedReply(cleaned, reply)
        return reply
    }

    private suspend fun consumePendingConfirmation(message: String): EddyAiReply? {
        val pending = pendingActions ?: return null
        if (System.currentTimeMillis() - pendingAt > CONFIRMATION_TTL_MS) {
            clearPending()
            return null
        }
        val normalized = normalize(message)
        if (normalized in NEGATIVE_CONFIRMATIONS) {
            clearPending()
            return EddyAiReply("Cancelado.", false, emptyList())
        }
        if (normalized !in POSITIVE_CONFIRMATIONS) return null

        clearPending()
        val messages = executePlannedActions(pending)
        return EddyAiReply(
            messages.joinToString(" ").ifBlank { "Listo." },
            false,
            emptyList(),
        )
    }

    private fun clearPending() {
        pendingActions = null
        pendingAt = 0L
    }

    private suspend fun requestPlan(message: String, memoryContext: String): JSONObject? = withContext(Dispatchers.IO) {
        val base = resolvedBaseUrl()
        if (base.isBlank()) return@withContext null
        val connection = (URL("$base/plan").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            // Meta de baja latencia: fallar rápido y dejar que el cerebro local continúe.
            connectTimeout = 900
            readTimeout = 2_200
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "EDDY-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            val payload = JSONObject()
                .put("message", message)
                .put("memory_context", memoryContext.takeLast(8_000))
                .toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) null else JSONObject(body)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun requestWebSearch(query: String, memoryContext: String): EddyAiReply? = withContext(Dispatchers.IO) {
        val base = resolvedBaseUrl()
        if (base.isBlank()) return@withContext null
        val connection = (URL("$base/search").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5_000
            readTimeout = 18_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "EDDY-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            val payload = JSONObject()
                .put("message", query)
                .put("force_web", true)
                .put("memory_context", memoryContext.takeLast(8_000))
                .toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) return@withContext null
            val json = JSONObject(body)
            val text = json.optString("reply").trim()
            if (text.isBlank()) return@withContext null
            val sources = json.optJSONArray("sources").toWebSources()
            val evidence = buildEvidence(json.optJSONArray("evidence"))
            EddyAiReply(text, json.optBoolean("web_used", sources.isNotEmpty()), sources, evidence)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray?.toWebSources(): List<EddyWebSource> = buildList {
        val array = this@toWebSources ?: return@buildList
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val url = item.optString("url").trim()
            if (url.isBlank()) continue
            add(EddyWebSource(item.optString("title").trim().ifBlank { "Fuente web" }, url))
        }
    }

    private fun buildEvidence(array: JSONArray?): String = buildString {
        if (array == null) return@buildString
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val snippet = item.optString("snippet").trim()
            if (snippet.isNotBlank()) appendLine("- ${item.optString("title")}: $snippet")
        }
    }.trim()

    private fun extractWebQueries(actions: JSONArray): List<String> = buildList {
        for (i in 0 until actions.length()) {
            val item = actions.optJSONObject(i) ?: continue
            if (item.optString("type").trim().lowercase() != "web_search") continue
            val args = item.optJSONObject("args") ?: JSONObject()
            arg(args, "query", "text").takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun withoutWebActions(actions: JSONArray): JSONArray {
        val output = JSONArray()
        for (i in 0 until actions.length()) {
            val item = actions.optJSONObject(i) ?: continue
            if (item.optString("type").trim().lowercase() != "web_search") output.put(item)
        }
        return output
    }

    private suspend fun executePlannedActions(actions: JSONArray?): List<String> = withContext(Dispatchers.Main) {
        if (actions == null) return@withContext emptyList()
        buildList {
            for (i in 0 until actions.length()) {
                val item = actions.optJSONObject(i) ?: continue
                val type = item.optString("type").trim().lowercase()
                val args = item.optJSONObject("args") ?: JSONObject()
                val result = executeAction(type, args)
                result?.spokenMessage?.takeIf { it.isNotBlank() }?.let(::add)
                if (type in ACCESSIBILITY_ACTIONS) delay(70L)
            }
        }
    }

    private suspend fun executeAction(type: String, args: JSONObject): ActionResult? {
        return when (type) {
            "open_app" -> executor.openAppByName(arg(args, "app", "name"))
            "torch" -> executor.setTorch(boolArg(args, "enabled", true))
            "dial" -> executor.dial(arg(args, "number", "phone"))
            "sms" -> executor.composeMessage(arg(args, "number", "phone"), arg(args, "message", "text"))
            "whatsapp" -> executor.whatsappMessage(arg(args, "number", "phone").ifBlank { null }, arg(args, "message", "text"))
            "spotify" -> executor.playSpotify(arg(args, "query", "song"))
            "maps" -> executor.openMaps(arg(args, "query", "place", "destination"))
            "volume" -> executor.setVolume(intArg(args, "percent", 50))
            "brightness" -> executor.setBrightness(intArg(args, "percent", 50))
            "camera" -> executor.openCamera()
            "alarm" -> executor.setAlarm(intArg(args, "hour", 7), intArg(args, "minute", 0), arg(args, "label").ifBlank { null })
            "timer" -> executor.setTimer(intArg(args, "seconds", 60), arg(args, "label").ifBlank { null })
            "system_panel" -> executor.openSystemPanel(systemPanel(arg(args, "panel", "name")))
            "back" -> accessibilityWithRetry("Atrás.") { it.goBack() }
            "home" -> accessibilityWithRetry("Inicio.") { it.goHome() }
            "recents" -> accessibilityWithRetry("Recientes.") { it.openRecents() }
            "notifications" -> accessibilityWithRetry("Notificaciones.") { it.openNotifications() }
            "quick_settings" -> accessibilityWithRetry("Ajustes rápidos.") { it.openQuickSettings() }
            "click_text" -> accessibilityWithRetry("Listo.") { it.clickText(arg(args, "text", "label")) }
            "type_text" -> accessibilityWithRetry("Listo.") { it.setTextInFocusedField(arg(args, "text", "value")) }
            "scroll_forward" -> accessibilityWithRetry("Listo.") { it.scrollForward() }
            "scroll_backward" -> accessibilityWithRetry("Listo.") { it.scrollBackward() }
            else -> null
        }
    }

    private suspend fun accessibilityWithRetry(
        success: String,
        action: (EddyAccessibilityService) -> Boolean,
    ): ActionResult {
        val service = EddyAccessibilityService.instance
            ?: return ActionResult(false, "Necesito que activés el servicio de accesibilidad de EDDY para hacer eso.")
        if (runCatching { action(service) }.getOrDefault(false)) return ActionResult(true, success)
        delay(140L)
        val retried = runCatching { action(service) }.getOrDefault(false)
        return ActionResult(retried, if (retried) success else "No pude completar esa acción en pantalla. Probé dos veces.")
    }

    private fun systemPanel(value: String): SystemPanel = when (normalize(value)) {
        "wifi", "wi fi" -> SystemPanel.WIFI
        "bluetooth" -> SystemPanel.BLUETOOTH
        "internet", "datos", "conectividad" -> SystemPanel.INTERNET
        "location", "ubicacion", "gps" -> SystemPanel.LOCATION
        "nfc" -> SystemPanel.NFC
        "airplane", "modo avion", "avion" -> SystemPanel.AIRPLANE
        else -> SystemPanel.SETTINGS
    }

    private fun arg(json: JSONObject, vararg keys: String): String {
        for (key in keys) json.optString(key).trim().takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    private fun intArg(json: JSONObject, key: String, fallback: Int): Int {
        val raw = json.opt(key) ?: return fallback
        return when (raw) {
            is Number -> raw.toInt()
            else -> raw.toString().filter { it.isDigit() || it == '-' }.toIntOrNull() ?: fallback
        }
    }

    private fun boolArg(json: JSONObject, key: String, fallback: Boolean): Boolean {
        if (!json.has(key)) return fallback
        return when (val raw = json.opt(key)) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> normalize(raw.toString()) in setOf("true", "1", "on", "yes", "si", "encender", "encendido", "prender", "prendido")
        }
    }

    private fun findLearnedReply(message: String): EddyAiReply? {
        val target = normalize(message)
        if (target.length < 5) return null
        val targetTokens = tokens(target)
        var best: KnowledgeEntry? = null
        var bestScore = 0.0
        val now = System.currentTimeMillis()
        for (entry in readKnowledge()) {
            if (now - entry.savedAt > KNOWLEDGE_TTL_MS) continue
            val candidate = normalize(entry.question)
            val score = if (candidate == target) 1.0 else similarity(targetTokens, tokens(candidate))
            if (score > bestScore) {
                bestScore = score
                best = entry
            }
        }
        val hit = best?.takeIf { bestScore >= .88 } ?: return null
        return EddyAiReply(hit.answer, false, emptyList())
    }

    private fun rememberLearnedReply(question: String, reply: EddyAiReply) {
        if (question.length < 5 || reply.text.length < 2) return
        val entries = readKnowledge().toMutableList()
        val normalized = normalize(question)
        entries.removeAll { normalize(it.question) == normalized }
        entries += KnowledgeEntry(question.take(500), reply.text.take(6_000), System.currentTimeMillis())
        while (entries.size > 120) entries.removeAt(0)
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("q", it.question).put("a", it.answer).put("t", it.savedAt)) }
        knowledgePrefs.edit().putString(KEY_KNOWLEDGE, array.toString()).apply()
    }

    private fun readKnowledge(): List<KnowledgeEntry> {
        val raw = knowledgePrefs.getString(KEY_KNOWLEDGE, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val q = item.optString("q").trim()
                    val a = item.optString("a").trim()
                    if (q.isNotBlank() && a.isNotBlank()) add(KnowledgeEntry(q, a, item.optLong("t")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9ñ ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun tokens(value: String) = value.split(' ').filter { it.length >= 3 && it !in STOP_WORDS }.toSet()

    private fun similarity(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size

    private data class KnowledgeEntry(val question: String, val answer: String, val savedAt: Long)

    companion object {
        private const val KNOWLEDGE_PREFS = "eddy_learned_knowledge_v1"
        private const val KEY_KNOWLEDGE = "entries"
        private const val KNOWLEDGE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        private const val CONFIRMATION_TTL_MS = 30_000L

        private val ACCESSIBILITY_ACTIONS = setOf(
            "back", "home", "recents", "notifications", "quick_settings",
            "click_text", "type_text", "scroll_forward", "scroll_backward",
        )
        private val POSITIVE_CONFIRMATIONS = setOf(
            "si", "si hazlo", "si hacelo", "dale", "confirmo", "confirmado", "correcto", "procede", "adelante", "hacele",
        )
        private val NEGATIVE_CONFIRMATIONS = setOf(
            "no", "no lo hagas", "cancela", "cancelalo", "cancelar", "dejalo", "mejor no",
        )
        private val STOP_WORDS = setOf(
            "que", "como", "para", "por", "con", "una", "uno", "del", "las", "los", "esto", "esta", "este",
            "me", "mi", "es", "son", "hay", "quiero", "puedes", "puede", "favor",
        )
    }
}
