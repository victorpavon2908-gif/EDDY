package com.niko.assistant.background

import android.content.Context
import android.content.Intent
import com.niko.assistant.ai.LeoBrand
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.voice.LeoVoiceDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    const val ACTION_RUNTIME_STATE = "com.eddy.assistant.RUNTIME_STATE"

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

    /** In-memory reactive state — always up to date inside each Android process. */
    private val _stateFlow = MutableStateFlow(Snapshot())
    val stateFlow: StateFlow<Snapshot> get() = _stateFlow.asStateFlow()

    /**
     * El servicio de voz corre en :voice para que un fallo nativo no mate la UI.
     * Por eso sincronizamos el snapshot por broadcast explícito en vez de depender
     * de SharedPreferences multi-proceso (Android no garantiza coherencia ahí).
     */
    fun acceptExternalState(intent: Intent) {
        if (intent.action != ACTION_RUNTIME_STATE) return
        val raw = intent.getStringExtra(EXTRA_SNAPSHOT) ?: return
        decodeSnapshot(raw)?.let { _stateFlow.value = it }
    }

    private fun publishExternal(context: Context) {
        val intent = Intent(ACTION_RUNTIME_STATE)
            .setPackage(context.packageName)
            .putExtra(EXTRA_SNAPSHOT, encodeSnapshot(_stateFlow.value))
        context.sendBroadcast(intent)
    }

    /**
     * Seeds the in-memory [StateFlow] from persisted SharedPreferences.
     * Call once from the Application or Service when the process starts.
     */
    fun init(context: Context) {
        _stateFlow.value = read(context)
    }

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
        _stateFlow.value = _stateFlow.value.copy(
            inputState = state,
            inputStatus = LeoBrand.publicText(status),
            state = if (state != InputState.READY) State.IDLE else _stateFlow.value.state,
            heardText = if (state != InputState.READY) "" else _stateFlow.value.heardText,
        )
        publishExternal(context)
    }

    fun setInputStatus(context: Context, value: String) {
        val publicValue = LeoBrand.publicText(value)
        edit(context) { putString(KEY_INPUT_STATUS, publicValue) }
        _stateFlow.value = _stateFlow.value.copy(inputStatus = publicValue)
        publishExternal(context)
    }

    fun setSearching(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_SEARCHING, value) }
        _stateFlow.value = _stateFlow.value.copy(webSearching = value)
        publishExternal(context)
    }

    fun setRunning(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_RUNNING, value) }
        _stateFlow.value = _stateFlow.value.copy(running = value)
        publishExternal(context)
    }

    fun setVoiceStatus(context: Context, value: String) {
        val publicValue = LeoBrand.publicText(value)
        edit(context) { putString(KEY_VOICE_STATUS, publicValue) }
        _stateFlow.value = _stateFlow.value.copy(voiceStatus = publicValue)
        publishExternal(context)
    }

    fun setVoiceReady(context: Context, value: Boolean) {
        edit(context) { putBoolean(KEY_VOICE_READY, value) }
        _stateFlow.value = _stateFlow.value.copy(voiceReady = value)
        publishExternal(context)
    }

    fun setState(context: Context, value: State) {
        edit(context) { putString(KEY_STATE, value.name) }
        _stateFlow.value = _stateFlow.value.copy(state = value)
        publishExternal(context)
    }

    fun setBrainProgress(
        context: Context,
        state: BrainState,
        status: String,
        downloadedBytes: Long = 0L,
        totalBytes: Long = 0L,
    ) {
        val progress = if (state == BrainState.READY) 100 else brainProgressPercent(downloadedBytes, totalBytes)
        val publicStatus = LeoBrand.publicText(status)
        edit(context) {
            putString(KEY_BRAIN_STATE, state.name)
            putInt(KEY_BRAIN_PROGRESS, progress)
            putString(KEY_BRAIN_STATUS, publicStatus)
            putLong(KEY_BRAIN_DOWNLOADED_BYTES, downloadedBytes.coerceAtLeast(0L))
            putLong(KEY_BRAIN_TOTAL_BYTES, totalBytes.coerceAtLeast(0L))
        }
        _stateFlow.value = _stateFlow.value.copy(
            brainState = state,
            brainProgress = progress,
            brainStatus = publicStatus,
            brainDownloadedBytes = downloadedBytes.coerceAtLeast(0L),
            brainTotalBytes = totalBytes.coerceAtLeast(0L),
        )
        publishExternal(context)
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
        _stateFlow.value = _stateFlow.value.copy(heardText = value)
        publishExternal(context)
    }

    /** Token previews stay in memory. Persist the completed answer once, not every token. */
    fun previewResponse(value: String) {
        _stateFlow.value = _stateFlow.value.copy(
            responseText = LeoBrand.publicText(value), webUsed = false, webSources = emptyList(),
        )
    }

    fun setResponse(context: Context, value: String) {
        val publicValue = LeoBrand.publicText(value)
        edit(context) {
            putString(KEY_RESPONSE, publicValue)
            putBoolean(KEY_WEB_USED, false)
            putString(KEY_WEB_SOURCES, "[]")
        }
        _stateFlow.value = _stateFlow.value.copy(
            responseText = publicValue,
            webUsed = false,
            webSources = emptyList(),
        )
        publishExternal(context)
    }

    fun setAiResponse(
        context: Context,
        value: String,
        webUsed: Boolean,
        sources: List<NikoWebSource>,
    ) {
        val publicValue = LeoBrand.publicText(value)
        edit(context) {
            putString(KEY_RESPONSE, publicValue)
            putBoolean(KEY_WEB_USED, webUsed)
            putString(KEY_WEB_SOURCES, encodeSources(sources))
        }
        _stateFlow.value = _stateFlow.value.copy(
            responseText = publicValue,
            webUsed = webUsed,
            webSources = sources,
        )
        publishExternal(context)
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
        _stateFlow.value = _stateFlow.value.copy(
            state = State.IDLE,
            heardText = "",
            responseText = "Decí LEO para activarme.",
            inputStatus = "Micrófono en pausa",
            inputState = InputState.STOPPED,
            webSearching = false,
            running = false,
            voiceReady = false,
            voiceStatus = "Voz en pausa",
            webUsed = false,
            webSources = emptyList(),
        )
        publishExternal(context)
    }

    private fun encodeSnapshot(snapshot: Snapshot): String = JSONObject()
        .put("state", snapshot.state.name)
        .put("heard", snapshot.heardText)
        .put("response", snapshot.responseText)
        .put("running", snapshot.running)
        .put("voiceReady", snapshot.voiceReady)
        .put("voiceStatus", snapshot.voiceStatus)
        .put("inputState", snapshot.inputState.name)
        .put("inputStatus", snapshot.inputStatus)
        .put("searching", snapshot.webSearching)
        .put("webUsed", snapshot.webUsed)
        .put("webSources", encodeSources(snapshot.webSources))
        .put("brainState", snapshot.brainState.name)
        .put("brainProgress", snapshot.brainProgress)
        .put("brainStatus", snapshot.brainStatus)
        .put("brainDownloaded", snapshot.brainDownloadedBytes)
        .put("brainTotal", snapshot.brainTotalBytes)
        .toString()

    private fun decodeSnapshot(raw: String): Snapshot? = runCatching {
        val json = JSONObject(raw)
        Snapshot(
            state = State.valueOf(json.optString("state", State.IDLE.name)),
            heardText = json.optString("heard"),
            responseText = json.optString("response", "Decí LEO para activarme."),
            running = json.optBoolean("running"),
            voiceReady = json.optBoolean("voiceReady"),
            voiceStatus = json.optString("voiceStatus", "Preparando voz de respuesta"),
            inputState = InputState.valueOf(json.optString("inputState", InputState.STOPPED.name)),
            inputStatus = json.optString("inputStatus", "Micrófono sin iniciar"),
            webSearching = json.optBoolean("searching"),
            webUsed = json.optBoolean("webUsed"),
            webSources = decodeSources(json.optString("webSources", "[]")),
            brainState = BrainState.valueOf(json.optString("brainState", BrainState.WAITING.name)),
            brainProgress = json.optInt("brainProgress", 0).coerceIn(0, 100),
            brainStatus = json.optString("brainStatus", "Cerebro local pendiente"),
            brainDownloadedBytes = json.optLong("brainDownloaded", 0L).coerceAtLeast(0L),
            brainTotalBytes = json.optLong("brainTotal", 0L).coerceAtLeast(0L),
        )
    }.getOrNull()

    private fun encodeSources(sources: List<NikoWebSource>): String {
        val array = JSONArray()
        sources.take(64).forEach { source ->
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

    private const val EXTRA_SNAPSHOT = "snapshot"

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
