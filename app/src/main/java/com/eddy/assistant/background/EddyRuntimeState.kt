package com.eddy.assistant.background

import android.content.Context

object EddyRuntimeState {
    private const val PREFS = "eddy_runtime_state"
    private const val KEY_STATE = "state"
    private const val KEY_HEARD = "heard"
    private const val KEY_RESPONSE = "response"
    private const val KEY_RUNNING = "running"
    private const val KEY_VOICE_READY = "voice_ready"

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
        )
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
        edit(context) { putString(KEY_RESPONSE, value) }
    }

    fun reset(context: Context) {
        edit(context) {
            putString(KEY_STATE, State.IDLE.name)
            putString(KEY_HEARD, "")
            putString(KEY_RESPONSE, "Di EDDY para activarme.")
            putBoolean(KEY_RUNNING, false)
            putBoolean(KEY_VOICE_READY, false)
        }
    }

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
