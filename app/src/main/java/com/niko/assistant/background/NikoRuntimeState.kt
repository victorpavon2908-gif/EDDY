package com.niko.assistant.background

import android.content.Context
import com.niko.assistant.ai.LeoBrand
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.voice.LeoVoiceDiagnostics
import org.json.JSONArray
import org.json.JSONObject

object NikoRuntimeState {
    // Keep the legacy preference name so an update preserves the current session/state.
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
    private const val KEY_BRAIN_STATE = "brain_state"
    private const val KEY_BRAIN_PROGRESS = "brain_progress"
    private const val KEY_BRAIN_STATUS = "brain_status"
    private const val KEY_BRAIN_DOWNLOADED_BYTES = "brain_downloaded_bytes"
    private const val KEY_BRAIN_TOTAL_BYTES = "brain_total_bytes"

    enum class InputState { STOPPED, PREPARING, READY, ERROR }

    enum class BrainState { WAITING, CHECKING, DOWNLOADING, VERIFYING, INSTALLING, READY, ERROR }

    enum class State {
        IDLE,
        LISTENING,
        THINKING,
        SPEAKING,
    }

    data class Snapshot(
        val state: State = State.IDLE,
        val heardText: String = "",
        val responseText: String = "Decí LEO para activarme.",
        val running: Boolean = false,
        val voiceReady: Boolean = false,
        val voiceStatus: String = "Preparando voz de respuesta",
        val inputState: InputState = InputState.STOPPED,
        val inputStatus: String = "Micrófono sin iniciar",
        val webSearching: Boolean = false,
        val webUsed: Boolean = false,
        val webSources: List<NikoWebSource> = emptyList(),
        val brainState: BrainState = BrainState.WAITING,
        val brainProgress: Int = 0,
        val brainStatus: String = "Cerebro local pendiente",
        val brainDownloadedBytes: Long = 0L,
        val brainTotalBytes: Long = 0L,
    )

    fun read(context: Context): Snapshot {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val state = runCatching {
            State.valueOf(prefs.getString(KEY_STATE, State.IDLE.name) ?: State.IDLE.name)
        }.getOrDefault(State.IDLE)
        val brainState = runCatching {
            BrainState.valueOf(prefs.getString(KEY_BRAIN_STATE, BrainState.WAITING.name).orEmpty())
        }.getOrDefault(BrainState.WAITING)

        return Snapshot(
            state = state,
            heardText = prefs.getString(KEY_HEARD, "").orEmpty(),
            responseText = LeoBrand.publicText(
                prefs.getString(KEY_RESPONSE, "Decí LEO para activarme.")
                    .orEmpty()
                    .ifBlank { "Decí LEO para activarme." },
            ),
            running = prefs.getBoolean(KEY_RUNNING, false),
            voiceReady = prefs.getBoolean(KEY_VOICE_READY, false),
            voiceStatus = LeoBrand.publicText(prefs.getString(KEY_VOICE_STATUS, "Preparando voz de respuesta").orEmpty()),
            inputState = runCatching {
                InputState.valueOf(prefs.getString(KEY_INPUT_STATE, InputState.STOPPED.name).orEmpty())
            }.getOrDefault(InputState.STOPPED),
            inputStatus = LeoBrand.publicText(prefs.getString(KEY_INPUT_STATUS, "Micrófono sin iniciar").orEmpty()),
            webSearching = prefs.getBoolean(KEY_SEARCHING, false),
            webUsed = prefs.getBoolean(KEY_WEB_USED, false),
            webSources = decodeSources(prefs.getString(KEY_WEB_SOURCES, "[]").orEmpty()),
            brainState = brainState,
            brainProgress = prefs.getInt(KEY_BRAIN_PROGRESS, if (brainState == BrainState.READY) 100 else 0).coerceIn(0, 100),
            brainStatus = LeoBrand.publicText(
                prefs.getString(KEY_BRAIN_STATUS, "Cerebro local pendiente").orEmpty().ifBlank { "Cerebro local pendiente" },
            ),
            brainDownloadedBytes = prefs.getLong(KEY_BRAIN_DOWNLOADED_BYTES, 0L).coerceAtLeast(0L),
            brainTotalBytes = prefs.getLong(KEY_BRAIN_TOTAL_BYTES, 0L).coerceAtLeast(0L),
        )
    }

    fun setInput(context: Context, state: InputState, status: String) {
        LeoVoiceDiagnostics.recordInputState(state.name, status)
        edit(context) {
            putString(KEY_INPUT_STATE, state.name)
            putString(KEY_INPUT_STATUS, LeoBrand.publicText(status))
            if (state != InputState.READY) {
                putString(KEY_STATE, State.IDLE.name)
                putString(KEY_HEARD, "")
            }
        }
    }

    fun setInputStatus(context: Context, value: String) {
        edit(context) { putString(KEY_INPUT_STATUS, LeoBrand.publicText(value)) }
    }

    fun setSearching(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_SEARCHING, value) }
    }

    fun setRunning(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_RUNNING, value) }
    }

    fun setVoiceStatus(context: Context, value: String) {
        edit(context) { putString(KEY_VOICE_STATUS, LeoBrand.publicText(value)) }
    }

    fun setVoiceReady(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_VOICE_READY, value) }
    }

    fun setState(context: Context, value: State) {
        edit(context) { putString(KEY_STATE, value.name) }
    }

    fun setBrainProgress(
        context: Context,
        state: BrainState,
        status: String,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
    ) {
        val progress = if (state == BrainState.READY) 100 else brainProgressPercent(downloadedBytes, totalBytes)
        edit(context) {
            putString(KEY_BRAIN_STATE, state.name)
            putInt(KEY_BRAIN_PROGRESS, progress)
            putString(KEY_BRAIN_STATUS, LeoBrand.publicText(status))
            putLong(KEY_BRAIN_DOWNLOADED_BYTES, downloadedBytes.coerceAtLeast(0L))
            putLong(KEY_BRAIN_TOTAL_BYTES, totalBytes.coerceAtLeast(0L))
        }
    }

    internal fun brainProgressPercent(downloadedBytes: Long, totalBytes: Long): Int {
        if (downloadedBytes <= 0L || totalBytes <= 0L) return 0
        val bounded = downloadedBytes.coerceAtMost(totalBytes)
        return ((bounded.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    fun setHeard(context: Context, value: String) {
        // User dictation is evidence, not UI branding: Nico/Niko can be real contact names.
        if (value.trim().equals("LEO", ignoreCase = true)) LeoVoiceDiagnostics.recordWake()
        edit(context) { putString(KEY_HEARD, value) }
    }

    fun setResponse(context: Context, value: String) {
        edit(context) {
            putString(KEY_RESPONSE, LeoBrand.publicText(value))
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
            putString(KEY_RESPONSE, LeoBrand.publicText(value))
            putBoolean(KEY_WEB_USED, webUsed)
            putString(KEY_WEB_SOURCES, encodeSources(sources))
        }
    }

    fun reset(context: Context) {
        LeoVoiceDiagnostics.recordInputState(InputState.STOPPED.name, "Micrófono en pausa")
        edit(context) {
            putString(KEY_STATE, State.IDLE.name)
            putString(KEY_HEARD, "")
            putString(KEY_RESPONSE, "Decí LEO para activarme.")
            putString(KEY_INPUT_STATUS, "Micrófono en pausa")
            putString(KEY_INPUT_STATE, InputState.STOPPED.name)
            putBoolean(KEY_SEARCHING, false)
            putBoolean(KEY_RUNNING, false)
            putBoolean(KEY_VOICE_READY, false)
            putString(KEY_VOICE_STATUS, "Voz en pausa")
            putBoolean(KEY_WEB_USED, false)
            putString(KEY_WEB_SOURCES, "[]")
            // Brain state intentionally survives a voice/service reset: the frozen brain is
            // independent persistent storage and a partial download can resume next launch.
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
