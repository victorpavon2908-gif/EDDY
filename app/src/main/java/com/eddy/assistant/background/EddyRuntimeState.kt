package com.eddy.assistant.background

import android.content.Context
import com.eddy.assistant.ai.EddyWebSource
import org.json.JSONArray
import org.json.JSONObject

object EddyRuntimeState {
    private const val PREFS = "eddy_runtime_state"
    private const val KEY_STATE = "state"
    private const val KEY_HEARD = "heard"
    private const val KEY_RESPONSE = "response"
    private const val KEY_RUNNING = "running"
    private const val KEY_VOICE_READY = "voice_ready"
    private const val KEY_INPUT_STATUS = "input_status"
    private const val KEY_SEARCHING = "searching"
    private const val KEY_WEB_USED = "web_used"
    private const val KEY_WEB_SOURCES = "web_sources"

    enum class State {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
    }

    data class Snapshot(
        val state: State = State.IDLE,
        val heardText: String = "",
        val responseText: String = "Di EDDY para activarme.",
        val running: Boolean = false,
        val voiceReady: Boolean = false,
        val inputStatus: String = "Micrófono sin iniciar",
        val webSearching: Boolean = false,
        val webUsed: Boolean = false,
        val webSources: List<EddyWebSource> = emptyList(),
    )

    fun read(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = runCatching {
            State.valueOf(prefs.getString(KEY_STATE, State.IDLE.name) ?: State.IDLE.name)
        }.getOrDefault(State.IDLE)

        return Snapshot(
            state = state,
            heardText = prefs.getString(KEY_HEARD, "").orEmpty(),
            responseText = prefs.getString(KEY_RESPONSE, "Di EDDY para activarme.")
                .orEmpty()
                .ifBlank { "Di EDDY para activarme." },
            running = prefs.getBoolean(KEY_RUNNING, false),
            voiceReady = prefs.getBoolean(KEY_VOICE_READY, false),
            inputStatus = prefs.getString(KEY_INPUT_STATUS, "Micrófono sin iniciar").orEmpty(),
            webSearching = prefs.getBoolean(KEY_SEARCHING, false),
            webUsed = prefs.getBoolean(KEY_WEB_USED, false),
            webSources = decodeSources(prefs.getString(KEY_WEB_SOURCES, "[]").orEmpty()),
        )
    }

    fun setInputStatus(context: Context, value: String) {
        edit(context) { putString(KEY_INPUT_STATUS, value) }
    }

    fun setSearching(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_SEARCHING, value) }
    }

    fun setRunning(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_RUNNING, value) }
    }

    fun setVoiceReady(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_VOICE_READY, value) }
    }

    fun setState(context: Context, value: State) {
        edit(context) { putString(KEY_STATE, value.name) }
    }

    fun setHeard(context: Context, value: String) {
        edit(context) { putString(KEY_HEARD, value) }
    }

    fun setResponse(context: Context, value: String) {
        edit(context) {
            putString(KEY_RESPONSE, value)
            putBoolean(KEY_WEB_USED, false)
            putString(KEY_WEB_SOURCES, "[]")
        }
    }

    fun setAiResponse(
        context: Context,
        value: String,
        webUsed: Boolean,
        sources: List<EddyWebSource>,
    ) {
        edit(context) {
            putString(KEY_RESPONSE, value)
            putBoolean(KEY_WEB_USED, webUsed)
            putString(KEY_WEB_SOURCES, encodeSources(sources))
        }
    }

    fun reset(context: Context) {
        edit(context) {
            putString(KEY_STATE, State.IDLE.name)
            putString(KEY_HEARD, "")
            putString(KEY_RESPONSE, "Di EDDY para activarme.")
            putString(KEY_INPUT_STATUS, "Micrófono en pausa")
            putBoolean(KEY_SEARCHING, false)
            putBoolean(KEY_RUNNING, false)
            putBoolean(KEY_VOICE_READY, false)
            putBoolean(KEY_WEB_USED, false)
            putString(KEY_WEB_SOURCES, "[]")
        }
    }

    private fun encodeSources(sources: List<EddyWebSource>): String {
        val array = JSONArray()
        sources.take(8).forEach { source ->
            array.put(
                JSONObject()
                    .put("title", source.title)
                    .put("url", source.url)
            )
        }
        return array.toString()
    }

    private fun decodeSources(raw: String): List<EddyWebSource> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (url.isBlank()) continue
                add(
                    EddyWebSource(
                        title = item.optString("title").trim().ifBlank { "Fuente web" },
                        url = url,
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private inline fun edit(
        context: Context,
        block: android.content.SharedPreferences.Editor.() -> Unit,
    ) {
        val editor = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
        editor.block()
        editor.apply()
    }
}
