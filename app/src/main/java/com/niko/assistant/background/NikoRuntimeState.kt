package com.niko.assistant.background

import android.content.Context
import com.niko.assistant.ai.NikoWebSource
import org.json.JSONArray
import org.json.JSONObject

object NikoRuntimeState {
    private const val PREFS = "niko_runtime_state"
    private const val KEY_STATE = "state"
    private const val KEY_HEARD = "heard"
    private const val KEY_RESPONSE = "response"
    private const val KEY_RUNNING = "running"
    private const val KEY_VOICE_READY = "voice_ready"
    private const val KEY_VOICE_STATUS = "voice_status"
    private const val KEY_INPUT_STATUS = "input_status"
    private const val KEY_INPUT_STATE = "input_state"
    private const val KEY_SEARCHING = "searching"
    private const val KEY_WEB_USED = "web_used"
    private const val KEY_WEB_SOURCES = "web_sources"

    enum class InputState { STOPPED, PREPARING, READY, ERROR }

    enum class State {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
    }

    data class Snapshot(
        val state: State = State.IDLE,
        val heardText: String = "",
        val responseText: String = "Di NIKO para activarme.",
        val running: Boolean = false,
        val voiceReady: Boolean = false,
        val voiceStatus: String = "Preparando voz de respuesta",
        val inputState: InputState = InputState.STOPPED,
        val inputStatus: String = "Micrófono sin iniciar",
        val webSearching: Boolean = false,
        val webUsed: Boolean = false,
        val webSources: List<NikoWebSource> = emptyList(),
    )

    fun read(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = runCatching {
            State.valueOf(prefs.getString(KEY_STATE, State.IDLE.name) ?: State.IDLE.name)
        }.getOrDefault(State.IDLE)

        return Snapshot(
            state = state,
            heardText = prefs.getString(KEY_HEARD, "").orEmpty(),
            responseText = prefs.getString(KEY_RESPONSE, "Di NIKO para activarme.")
                .orEmpty()
                .ifBlank { "Di NIKO para activarme." },
            running = prefs.getBoolean(KEY_RUNNING, false),
            voiceReady = prefs.getBoolean(KEY_VOICE_READY, false),
            voiceStatus = prefs.getString(KEY_VOICE_STATUS, "Preparando voz de respuesta").orEmpty(),
            inputState = runCatching {
                InputState.valueOf(prefs.getString(KEY_INPUT_STATE, InputState.STOPPED.name).orEmpty())
            }.getOrDefault(InputState.STOPPED),
            inputStatus = prefs.getString(KEY_INPUT_STATUS, "Micrófono sin iniciar").orEmpty(),
            webSearching = prefs.getBoolean(KEY_SEARCHING, false),
            webUsed = prefs.getBoolean(KEY_WEB_USED, false),
            webSources = decodeSources(prefs.getString(KEY_WEB_SOURCES, "[]").orEmpty()),
        )
    }

    fun setInput(context: Context, state: InputState, status: String) {
        edit(context) {
            putString(KEY_INPUT_STATE, state.name)
            putString(KEY_INPUT_STATUS, status)
            if (state != InputState.READY) {
                putString(KEY_STATE, State.IDLE.name)
                putString(KEY_HEARD, "")
            }
        }
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

    fun setVoiceStatus(context: Context, value: String) {
        edit(context) { putString(KEY_VOICE_STATUS, value) }
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
        sources: List<NikoWebSource>,
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
            putString(KEY_RESPONSE, "Di NIKO para activarme.")
            putString(KEY_INPUT_STATUS, "Micrófono en pausa")
            putString(KEY_INPUT_STATE, InputState.STOPPED.name)
            putBoolean(KEY_SEARCHING, false)
            putBoolean(KEY_RUNNING, false)
            putBoolean(KEY_VOICE_READY, false)
            putString(KEY_VOICE_STATUS, "Voz en pausa")
            putBoolean(KEY_WEB_USED, false)
            putString(KEY_WEB_SOURCES, "[]")
        }
    }

    private fun encodeSources(sources: List<NikoWebSource>): String {
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

    private fun decodeSources(raw: String): List<NikoWebSource> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url").trim()
                if (url.isBlank()) continue
                add(
                    NikoWebSource(
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
