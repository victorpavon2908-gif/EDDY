package com.niko.assistant.voice

import com.niko.assistant.compat.UpgradeIdentity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.text.Normalizer

/** Spanish offline speech with bounded automatic recovery, micro-prosody contours, and obsolete-callback rejection. */
class NikoTextToSpeech private constructor(
    context: Context,
    private val onReady: (Boolean) -> Unit,
    private val onSpeakingChanged: (Boolean) -> Unit,
    private val onVoiceSelected: (String) -> Unit,
    private val factory: (Context, String?, (Int) -> Unit) -> LeoSpeechEngine,
) : TextToSpeech.OnInitListener {
    constructor(context: Context, onReady: (Boolean) -> Unit = {}, onSpeakingChanged: (Boolean) -> Unit = {},
        onVoiceSelected: (String) -> Unit = {}) : this(context, onReady, onSpeakingChanged, onVoiceSelected,
        { app, name, listener -> AndroidLeoSpeechEngine(app, name, listener) })

    internal constructor(context: Context, factory: (Context, String?, (Int) -> Unit) -> LeoSpeechEngine,
        onReady: (Boolean) -> Unit = {}, onSpeakingChanged: (Boolean) -> Unit = {}, onVoiceSelected: (String) -> Unit = {}) :
        this(context, onReady, onSpeakingChanged, onVoiceSelected, factory)

    private val app = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val voicePrefs = app.getSharedPreferences(UpgradeIdentity.outputVoicePreferences, Context.MODE_PRIVATE)
    private val requestedEngine = voicePrefs.getString("system_engine", null)
    private var engine: LeoSpeechEngine? = null
    private var engineEpoch = 0L
    private var retryIndex = 0
    private var retryTask: Runnable? = null
    private var initWatchdog: Runnable? = null
    private var lastAttempt = -30_000L
    private var closed = false
    private val realtimeStopper: () -> Unit = { stop() }
    @Volatile private var ready = false
    val isReady: Boolean get() = ready
    var voiceDescription: String = "Preparando voz española sin conexión"
        private set
    @Volatile private var notificationEpoch = 0
    @Volatile private var currentUtterance: String? = null
    @Volatile private var currentPrefix: String? = null

    init { LeoRealtimeTurnBus.registerSpeechStopper(realtimeStopper); createEngine() }

    private fun releaseEngine() {
        val previous = engine
        engine = null
        runCatching { previous?.stop() }
        runCatching { previous?.shutdown() }
    }

    private fun createEngine() {
        if (closed) return
        retryTask = null
        ready = false
        releaseEngine()
        val generation = ++engineEpoch
        lastAttempt = SystemClock.elapsedRealtime()
        try {
            engine = factory(app, requestedEngine) { status ->
                mainHandler.post { if (!closed && generation == engineEpoch) initialize(generation, status) }
            }
            initWatchdog = Runnable { failEngine(generation, "Timeout esperando inicialización de Android TTS (10s)") }.also { mainHandler.postDelayed(it, 10_000L) }
        } catch (e: Exception) { failEngine(generation, "${e.javaClass.simpleName}: ${e.message ?: "error al crear TTS"}") }
    }

    override fun onInit(status: Int) { mainHandler.post { initialize(engineEpoch, status) } }

    private fun initialize(generation: Long, status: Int) {
        if (closed || generation != engineEpoch) return
        initWatchdog?.let(mainHandler::removeCallbacks); initWatchdog = null
        val active = engine ?: return
        val selection = if (status == TextToSpeech.SUCCESS) runCatching {
            active.prepare(voicePrefs.getString("system_voice", null), voicePrefs.getString("system_engine", null))
        }.getOrNull() else null
        if (selection == null) { failEngine(generation, "TTS status=$status o sin voz española sin conexión instalada"); return }
        val listener = object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) { mainHandler.post {
                if (!closed && generation == engineEpoch && utteranceId == "${currentPrefix}_0") {
                    LeoVoiceDiagnostics.recordSpeechStarted("Android TTS"); notifySpeaking(true)
                }
            } }
            override fun onDone(utteranceId: String) { mainHandler.post {
                if (!closed && generation == engineEpoch && utteranceId == currentUtterance) notifySpeaking(false)
            } }
            @Deprecated("Deprecated in Android API")
            override fun onError(utteranceId: String) { mainHandler.post {
                if (!closed && generation == engineEpoch && currentPrefix?.let { utteranceId.startsWith(it) } == true)
                    failEngine(generation, "UtteranceProgressListener.onError($utteranceId)")
            } }
            override fun onStop(utteranceId: String, interrupted: Boolean) { mainHandler.post {
                if (!closed && generation == engineEpoch && utteranceId == currentUtterance) notifySpeaking(false)
            } }
        }
        if (!runCatching { active.listen(listener) && active.setProsody(1.07f, 0.92f) }.getOrDefault(false)) {
            failEngine(generation, "No se pudo configurar listener o prosodia inicial de TTS"); return
        }
        voicePrefs.edit().putString("system_engine", selection.engine).putString("system_voice", selection.name).apply()
        voiceDescription = "Voz del teléfono · ${selection.locale} · sin conexión"
        ready = true
        retryIndex = 0
        onVoiceSelected(voiceDescription)
        onReady(true)
    }

    private fun failEngine(generation: Long, reason: String = "Fallo en motor TTS") {
        if (closed || generation != engineEpoch) return
        ++engineEpoch; ++notificationEpoch
        initWatchdog?.let(mainHandler::removeCallbacks); initWatchdog = null
        LeoRealtimeTurnBus.endSpeechReference(currentPrefix)
        currentPrefix = null; currentUtterance = null
        ready = false
        releaseEngine()
        runCatching { com.niko.assistant.background.NikoRuntimeState.reportError(app, "TTS", reason) }
        LeoVoiceDiagnostics.recordSpeechCancelled()
        onReady(false); onSpeakingChanged(false)
        val delays = longArrayOf(500L, 1_500L, 4_000L)
        if (retryIndex < delays.size) {
            voiceDescription = "Recuperando voz del teléfono…"
            retryTask = Runnable { createEngine() }.also { mainHandler.postDelayed(it, delays[retryIndex++]) }
        } else voiceDescription = "Voz no disponible. Revisá que haya una voz española instalada; podés reintentar."
        onVoiceSelected(voiceDescription)
    }

    /** A new request can retry after the bounded recovery sequence has cooled down. */
    fun ensureReady(): Boolean {
        if (!closed && !ready && engine == null && retryTask == null && SystemClock.elapsedRealtime() - lastAttempt >= 30_000L) {
            retryIndex = 0; createEngine()
        }
        return ready
    }

    internal fun normalizeForSpeech(text: String): String = prepareForNicaraguanSpeech(text)

    private fun prepareForNicaraguanSpeech(text: String): String {
        var value = com.niko.assistant.ai.NikoIdentity.forSpeech(text.trim())
        if (value.isBlank()) return value
        value = value
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("`([^`]+)`"), "$1")
            .replace(Regex("(?i)\\bwi[ -]?fi\\b"), "uái fái")
            .replace(Regex("(?i)\\bwhats ?app\\b"), "guátsap")
            .replace(Regex("(?i)\\byoutube\\b"), "yútub")
            .replace(Regex("(?i)\\bspotify\\b"), "espótifai")
            .replace(Regex("(?i)\\bbluetooth\\b"), "blútuz")
            .replace(Regex("(?i)\\bchatgpt\\b"), "chat ye pe te")
            .replace(Regex("(?i)\\bopenai\\b"), "ópen ei ái")
            .replace(Regex("(?i)\\bgoogle\\b"), "gúgol")
            .replace(Regex("(?i)\\bandroid\\b"), "ándroid")
            .replace(Regex("(?i)\\binstagram\\b"), "ínstagram")
            .replace(Regex("(?i)\\btik ?tok\\b"), "tík tok")
            .replace(Regex("(?i)\\bOK\\b"), "okey")
            .replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*°\\s*[Cc]\\b"), "$1 grados centígrados")
            .replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*°\\s*[Ff]\\b"), "$1 grados Fahrenheit")
            .replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*%"), "$1 por ciento")
            .replace(Regex("(?i)(\\d+(?:[.,]\\d+)?)\\s*km/h\\b"), "$1 kilómetros por hora")
            .replace(Regex("C\\$\\s*(\\d+(?:[.,]\\d+)?)"), "$1 córdobas")
            .replace(Regex("(?:US)?\\$\\s*(\\d+(?:[.,]\\d+)?)"), "$1 dólares")
            .replace(Regex("(?i)\\betc\\."), "etcétera")
            .replace(Regex("(?i)\\bp\\.\\s*ej\\."), "por ejemplo")
            .replace(Regex("\\bDr\\.\\s+"), "Doctor ")
            .replace(Regex("\\bDra\\.\\s+"), "Doctora ")
            .replace(Regex("\\bIng\\.\\s+"), "Ingeniero ")
            .replace(Regex("\\s+"), " ")
            .replace("…", ".")
            .trim()
        return Normalizer.normalize(value, Normalizer.Form.NFC)
    }

    private fun notifySpeaking(value: Boolean) {
        if (!value) LeoRealtimeTurnBus.endSpeechReference(currentPrefix)
        val epoch = notificationEpoch
        mainHandler.post { if (epoch == notificationEpoch) onSpeakingChanged(value) }
    }

    fun speak(text: String, prosody: SpeechProsody = SpeechProsody()): Boolean {
        if (!ensureReady() || closed) return false
        val active = engine ?: return false
        val spoken = prepareForNicaraguanSpeech(text)
        if (spoken.isBlank()) return false
        ++notificationEpoch
        val generation = engineEpoch
        try {
            val maximum = (TextToSpeech.getMaxSpeechInputLength() - 1).coerceAtLeast(80)
            val planned = SpeechProsody.planUtterance(
                spoken,
                base = prosody,
                firstLimit = minOf(56, maximum),
                nextLimit = minOf(240, maximum),
            )
            if (planned.isEmpty()) return false
            val id = "leo_reply_${System.nanoTime()}"
            LeoRealtimeTurnBus.beginSpeechReference(id, spoken)
            currentPrefix = id; currentUtterance = "${id}_${planned.lastIndex}"
            LeoVoiceDiagnostics.recordSpeechRequested("Android TTS")
            var lastSpeed = Float.NaN
            var lastPitch = Float.NaN
            for ((index, chunk) in planned.withIndex()) {
                if (chunk.speed != lastSpeed || chunk.pitch != lastPitch) {
                    if (!active.setProsody(chunk.speed, chunk.pitch)) {
                        failEngine(generation, "setProsody(${chunk.speed}, ${chunk.pitch}) falló"); return false
                    }
                    lastSpeed = chunk.speed
                    lastPitch = chunk.pitch
                }
                if (active.speak(chunk.text, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, "${id}_$index") != TextToSpeech.SUCCESS) {
                    failEngine(generation, "TextToSpeech.speak devolvió ERROR en bloque $index"); return false
                }
            }
            return true
        } catch (e: Exception) { failEngine(generation, "${e.javaClass.simpleName}: ${e.message ?: "error en speak"}"); return false }
    }

    fun stop() {
        ++notificationEpoch
        LeoRealtimeTurnBus.endSpeechReference(currentPrefix)
        currentPrefix = null; currentUtterance = null
        LeoVoiceDiagnostics.recordSpeechCancelled()
        runCatching { engine?.stop() }
        notifySpeaking(false)
    }

    fun shutdown() {
        if (closed) return
        closed = true; ++engineEpoch
        retryTask?.let(mainHandler::removeCallbacks); retryTask = null
        initWatchdog?.let(mainHandler::removeCallbacks); initWatchdog = null
        LeoRealtimeTurnBus.unregisterSpeechStopper(realtimeStopper)
        ready = false
        stop(); releaseEngine()
    }
}
