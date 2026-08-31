package com.eddy.assistant.ai

import android.content.Context
import com.eddy.assistant.BuildConfig
import com.eddy.assistant.actions.ActionExecutor
import com.eddy.assistant.devicecontrol.EddyAccessibilityService
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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

/** Local-first client. Unknown natural language can be planned remotely and executed on-device. */
class EddyAiClient(
    private val context: Context,
    private val baseUrlOverride: String? = null,
) {
    private val appContext = context.applicationContext
    private val executor by lazy { ActionExecutor(appContext) }
    private val knowledgePrefs by lazy {
        appContext.getSharedPreferences(KNOWLEDGE_PREFS, Context.MODE_PRIVATE)
    }

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
            connection.connectTimeout = 2_000
            connection.readTimeout = 3_000
            connection.setRequestProperty("Accept", "application/json")
            connection.responseCode in 200..299
        } catch (_: Exception) { false } finally { connection.disconnect() }
    }

    suspend fun reply(
        message: String,
        memoryContext: String,
        forceWeb: Boolean = false,
    ): EddyAiReply? = withContext(Dispatchers.IO) {
        val cleaned = message.trim()
        if (cleaned.isBlank()) return@withContext null

        if (!forceWeb) findLearnedReply(cleaned)?.let { return@withContext it }
        val base = resolvedBaseUrl()
        if (base.isBlank()) return@withContext null

        // Conversation/action planning uses /plan directly. Research keeps /search.
        val endpoint = "$base/${if (forceWeb) "search" else "plan"}"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            // Fast failure is intentional: local fallback must keep EDDY responsive.
            connectTimeout = if (forceWeb) 8_000 else 900
            readTimeout = if (forceWeb) 15_000 else 2_500
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "EDDY-Android/${BuildConfig.VERSION_NAME}")
        }

        try {
            val payload = JSONObject()
                .put("message", cleaned)
                .put("memory_context", memoryContext.takeLast(8_000))
                .put("force_web", forceWeb)
                .toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299 || body.isBlank()) return@withContext null
            val json = JSONObject(body)

            if (!forceWeb) {
                val needsConfirmation = json.optBoolean("needs_confirmation", false)
                val actions = json.optJSONArray("actions")
                val actionMessages = if (!needsConfirmation) executePlannedActions(actions) else emptyList()
                val modelReply = json.optString("reply").trim()
                val text = when {
                    needsConfirmation -> modelReply.ifBlank { "Necesito que me confirmés esa acción antes de hacerla." }
                    modelReply.isNotBlank() -> modelReply
                    actionMessages.isNotEmpty() -> actionMessages.joinToString(" ")
                    else -> "Aquí estoy."
                }
                val reply = EddyAiReply(text, false, emptyList())
                // Cache conversation knowledge only, never action requests: repeated actions must execute again.
                if (actions == null || actions.length() == 0) rememberLearnedReply(cleaned, reply)
                return@withContext reply
            }

            val text = json.optString("reply").trim()
            if (text.isBlank()) return@withContext null
            val sourcesJson = json.optJSONArray("sources")
            val sources = buildList {
                if (sourcesJson != null) for (i in 0 until sourcesJson.length()) {
                    val item = sourcesJson.optJSONObject(i) ?: continue
                    val url = item.optString("url").trim()
                    if (url.isNotBlank()) add(EddyWebSource(item.optString("title").ifBlank { "Fuente web" }, url))
                }
            }
            EddyAiReply(text, json.optBoolean("web_used", sources.isNotEmpty()), sources)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun executePlannedActions(actions: JSONArray?): List<String> = withContext(Dispatchers.Main) {
        if (actions == null) return@withContext emptyList()
        buildList {
            for (i in 0 until actions.length()) {
                val item = actions.optJSONObject(i) ?: continue
                val type = item.optString("type").trim().lowercase()
                val args = item.optJSONObject("args") ?: JSONObject()
                val result = when (type) {
                    "open_app" -> executor.openAppByName(arg(args, "app", "name"))
                    "torch" -> executor.setTorch(boolArg(args, "enabled", true))
                    "dial" -> executor.dial(arg(args, "number", "phone"))
                    "sms" -> executor.composeMessage(arg(args, "number", "phone"), arg(args, "message", "text"))
                    "whatsapp" -> executor.whatsappMessage(arg(args, "number", "phone").ifBlank { null }, arg(args, "message", "text"))
                    "spotify" -> executor.playSpotify(arg(args, "query", "song"))
                    "maps" -> executor.openMaps(arg(args, "query", "place", "destination"))
                    "web_search" -> executor.searchWeb(arg(args, "query"))
                    "volume" -> executor.setVolume(intArg(args, "percent", 50))
                    "brightness" -> executor.setBrightness(intArg(args, "percent", 50))
                    "camera" -> executor.openCamera()
                    "alarm" -> executor.setAlarm(intArg(args, "hour", 7), intArg(args, "minute", 0), arg(args, "label").ifBlank { null })
                    "timer" -> executor.setTimer(intArg(args, "seconds", 60), arg(args, "label").ifBlank { null })
                    "back" -> accessibilityResult(EddyAccessibilityService.instance?.goBack() == true, "Atrás.")
                    "home" -> accessibilityResult(EddyAccessibilityService.instance?.goHome() == true, "Inicio.")
                    "recents" -> accessibilityResult(EddyAccessibilityService.instance?.openRecents() == true, "Recientes.")
                    "notifications" -> accessibilityResult(EddyAccessibilityService.instance?.openNotifications() == true, "Notificaciones.")
                    "quick_settings" -> accessibilityResult(EddyAccessibilityService.instance?.openQuickSettings() == true, "Ajustes rápidos.")
                    "click_text" -> accessibilityResult(EddyAccessibilityService.instance?.clickText(arg(args, "text", "label")) == true, "Listo.")
                    "type_text" -> accessibilityResult(EddyAccessibilityService.instance?.setTextInFocusedField(arg(args, "text", "value")) == true, "Listo.")
                    "scroll_forward" -> accessibilityResult(EddyAccessibilityService.instance?.scrollForward() == true, "Listo.")
                    "scroll_backward" -> accessibilityResult(EddyAccessibilityService.instance?.scrollBackward() == true, "Listo.")
                    else -> null
                }
                result?.spokenMessage?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun accessibilityResult(ok: Boolean, success: String) = com.eddy.assistant.actions.ActionResult(
        ok,
        if (ok) success else "Necesito que activés el servicio de accesibilidad de EDDY para hacer eso.",
    )

    private fun arg(json: JSONObject, vararg keys: String): String {
        for (key in keys) json.optString(key).trim().takeIf { it.isNotBlank() }?.let { return it }
        return ""
    }

    private fun intArg(json: JSONObject, key: String, fallback: Int): Int {
        val raw = json.opt(key) ?: return fallback
        return when (raw) { is Number -> raw.toInt(); else -> raw.toString().filter { it.isDigit() || it == '-' }.toIntOrNull() ?: fallback }
    }

    private fun boolArg(json: JSONObject, key: String, fallback: Boolean): Boolean {
        if (!json.has(key)) return fallback
        return when (val raw = json.opt(key)) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            else -> raw.toString().lowercase() in setOf("true", "1", "on", "yes", "si", "sí", "encender", "encendido")
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
            if (score > bestScore) { bestScore = score; best = entry }
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
                    val q = item.optString("q").trim(); val a = item.optString("a").trim()
                    if (q.isNotBlank() && a.isNotBlank()) add(KnowledgeEntry(q, a, item.optLong("t")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9ñ ]+"), " ").replace(Regex("\\s+"), " ").trim()
    private fun tokens(value: String) = value.split(' ').filter { it.length >= 3 && it !in STOP_WORDS }.toSet()
    private fun similarity(a: Set<String>, b: Set<String>): Double = if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size
    private data class KnowledgeEntry(val question: String, val answer: String, val savedAt: Long)

    companion object {
        private const val KNOWLEDGE_PREFS = "eddy_learned_knowledge_v1"
        private const val KEY_KNOWLEDGE = "entries"
        private const val KNOWLEDGE_TTL_MS = 30L * 24L * 60L * 60L * 1_000L
        private val STOP_WORDS = setOf("que","como","para","por","con","una","uno","del","las","los","esto","esta","este","me","mi","es","son","hay","quiero","puedes","puede","favor")
    }
}
