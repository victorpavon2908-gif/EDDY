package com.eddy.assistant.ai

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class PlannedAction(val type: String, val args: Map<String, String>)
data class EddyActionPlan(val reply: String, val actions: List<PlannedAction>, val needsConfirmation: Boolean)

/** Converts unrestricted natural language into a small, validated action vocabulary. */
class EddyActionPlanner(private val context: Context) {
    suspend fun plan(message: String, memoryContext: String): EddyActionPlan? = withContext(Dispatchers.IO) {
        val base = EddyAiSettings.baseUrl(context).trimEnd('/')
        if (base.isBlank()) return@withContext null
        val connection = (URL("$base/plan").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12_000; readTimeout = 45_000; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            val payload = JSONObject().put("message", message).put("memory_context", memoryContext.takeLast(12_000)).toString()
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body); val array = json.optJSONArray("actions")
            val actions = buildList {
                if (array != null) for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val type = item.optString("type").trim().lowercase()
                    if (type !in ALLOWED) continue
                    val argsJson = item.optJSONObject("args") ?: JSONObject(); val args = mutableMapOf<String,String>()
                    argsJson.keys().forEach { key -> args[key] = argsJson.optString(key) }
                    add(PlannedAction(type, args))
                }
            }
            EddyActionPlan(json.optString("reply").trim(), actions, json.optBoolean("needs_confirmation", false))
        } catch (_: Exception) { null } finally { connection.disconnect() }
    }
    companion object {
        private val ALLOWED = setOf("open_app","torch","dial","sms","whatsapp","spotify","alarm","timer","maps","web_search","volume","brightness","system_panel","camera","back","home","recents","notifications","quick_settings","click_text","type_text","scroll_forward","scroll_backward")
    }
}
