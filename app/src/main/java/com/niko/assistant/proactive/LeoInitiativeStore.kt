package com.niko.assistant.proactive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Additive preferences; no change to existing alarms, database or stored identities. */
class LeoInitiativeStore(context: Context) {
    private val prefs = context.getSharedPreferences("leo_initiative_v1", Context.MODE_PRIVATE)
    fun read(): LeoInitiativeEngine.State = runCatching {
        val json = JSONObject(prefs.getString("state", "{}").orEmpty())
        val suppressed = json.optJSONObject("suppressed") ?: JSONObject()
        val history = json.optJSONArray("history") ?: JSONArray()
        LeoInitiativeEngine.State(json.optString("day"), json.optInt("count"), json.optLong("next"),
            suppressed.keys().asSequence().take(100).associateWith { suppressed.getLong(it) },
            (0 until history.length()).toList().takeLast(40).map { i -> history.getJSONObject(i).let {
                LeoInitiativeEngine.Event(it.getString("id"), it.getString("reason"), it.getLong("at"), it.getString("outcome"))
            } })
    }.getOrDefault(LeoInitiativeEngine.State())
    fun save(state: LeoInitiativeEngine.State) {
        val history = JSONArray(state.history.map { JSONObject().put("id", it.id).put("reason", it.reason)
            .put("at", it.at).put("outcome", it.outcome) })
        prefs.edit().putString("state", JSONObject().put("day", state.day).put("count", state.count)
            .put("next", state.nextAllowed).put("suppressed", JSONObject(state.suppressed)).put("history", history).toString()).apply()
    }
    fun clear() { prefs.edit().clear().apply() }
}
