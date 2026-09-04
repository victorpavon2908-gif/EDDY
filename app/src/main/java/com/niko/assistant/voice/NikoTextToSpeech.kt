package com.niko.assistant.voice

import com.niko.assistant.compat.UpgradeIdentity

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.text.Normalizer

/** Voz del sistema con perfil latino/nicaragüense y soporte de interrupción por voz. */
class NikoTextToSpeech(
    context: Context,
    private val onReady: (Boolean) -> Unit = {},
    private val onSpeakingChanged: (Boolean) -> Unit = {},
    private val onVoiceSelected: (String) -> Unit = {},
) : TextToSpeech.OnInitListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val voicePrefs = context.applicationContext.getSharedPreferences(UpgradeIdentity.outputVoicePreferences, Context.MODE_PRIVATE)
    private val requestedEngine = voicePrefs.getString("system_engine", null)
    private val tts = TextToSpeech(context.applicationContext, this, requestedEngine)
    private val realtimeStopper: () -> Unit = { stop() }
    @Volatile private var ready = false
    val isReady: Boolean get() = ready
    var voiceDescription: String = "Falta una voz española instalada para usar sin conexión"
        private set
    @Volatile private var notificationEpoch = 0
    @Volatile private var currentUtterance: String? = null
    @Volatile private var currentPrefix: String? = null

    init {
        LeoRealtimeTurnBus.registerSpeechStopper(realtimeStopper)
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            ready = configureNicaraguanLatinVoice()
            tts.setSpeechRate(1.03f)
            tts.setPitch(0.92f)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {
                    if (utteranceId == "${currentPrefix}_0") {
                        LeoVoiceDiagnostics.recordSpeechStarted("Android TTS")
                        notifySpeaking(true)
                    }
                }
                override fun onDone(utteranceId: String) { if (utteranceId == currentUtterance) notifySpeaking(false) }
                @Deprecated("Deprecated in Android API")
                override fun onError(utteranceId: String) {
                    if (currentPrefix?.let { utteranceId.startsWith(it) } == true) {
                        LeoVoiceDiagnostics.recordSpeechCancelled()
                        notifyFailure()
                    }
                }
                override fun onStop(utteranceId: String, interrupted: Boolean) {
                    if (utteranceId == currentUtterance) notifySpeaking(false)
                }
            })
        }
        onReady(ready)
    }

    private fun configureNicaraguanLatinVoice(): Boolean {
        val voices = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        val engine = requestedEngine?.takeIf { requested -> tts.engines.any { it.name == requested } } ?: tts.defaultEngine
        val saved = voicePrefs.getString("system_voice", null).takeIf {
            voicePrefs.getString("system_engine", null) == engine
        }
        val candidates = voices.map {
            OfflineVoiceSelector.Candidate(
                it.name,
                it.locale.language,
                it.locale.country,
                it.quality,
                it.latency,
                it.isNetworkConnectionRequired,
                it.features.orEmpty(),
            )
        }
        for (candidate in OfflineVoiceSelector.ranked(candidates, saved)) {
            val voice = voices.first { it.name == candidate.name }
            if (runCatching { tts.setVoice(voice) == TextToSpeech.SUCCESS }.getOrDefault(false)) {
                voicePrefs.edit().putString("system_engine", engine).putString("system_voice", voice.name).apply()
                voiceDescription = "Voz del teléfono · ${voice.locale.toLanguageTag()} · sin conexión"
                onVoiceSelected(voiceDescription)
                return true
            }
        }
        onVoiceSelected(voiceDescription)
        return false
    }

    private fun prepareForNicaraguanSpeech(text: String): String {
        var value = com.niko.assistant.ai.NikoIdentity.forSpeech(text.trim())
        if (value.isBlank()) return value
        value = value
            .replace(Regex("(?i)\\bwi[ -]?fi\\b"), "uái fái")
            .replace(Regex("(?i)\\bwhats ?app\\b"), "guátsap")
            .replace(Regex("(?i)\\byoutube\\b"), "yútub")
            .replace(Regex("(?i)\\bspotify\\b"), "espótifai")
            .replace(Regex("(?i)\\bbluetooth\\b"), "blútuz")
            .replace(Regex("(?i)\\bOK\\b"), "okey")
            .replace(Regex("\\s+"), " ")
            .replace("…", ".")
            .trim()
        return Normalizer.normalize(value, Normalizer.Form.NFC)
    }

    private fun notifySpeaking(value: Boolean) {
        val epoch = notificationEpoch
        mainHandler.post { if (epoch == notificationEpoch) onSpeakingChanged(value) }
    }

    private fun notifyFailure() {
        ready = false
        val epoch = notificationEpoch
        mainHandler.post { if (epoch == notificationEpoch) { onReady(false); onSpeakingChanged(false) } }
    }

    fun speak(text: String, prosody: SpeechProsody = SpeechProsody()): Boolean {
        if (!ready) return false
        val spoken = prepareForNicaraguanSpeech(text)
        if (spoken.isBlank()) return false
        ++notificationEpoch
        tts.setSpeechRate(prosody.speed.coerceIn(0.85f, 1.15f))
        tts.setPitch(prosody.pitch.coerceIn(0.85f, 1.05f))
        val chunks = SpeechProsody.chunks(spoken, minOf(320, TextToSpeech.getMaxSpeechInputLength() - 1))
        val id = "leo_reply_${System.nanoTime()}"
        currentPrefix = id
        currentUtterance = "${id}_${chunks.lastIndex}"
        LeoVoiceDiagnostics.recordSpeechRequested("Android TTS")
        for ((index, chunk) in chunks.withIndex()) {
            val result = tts.speak(
                chunk,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "${id}_$index",
            )
            if (result != TextToSpeech.SUCCESS) {
                ready = false
                LeoVoiceDiagnostics.recordSpeechCancelled()
                stop()
                onReady(false)
                return false
            }
        }
        return true
    }

    fun stop() {
        ++notificationEpoch
        currentPrefix = null
        currentUtterance = null
        LeoVoiceDiagnostics.recordSpeechCancelled()
        tts.stop()
        notifySpeaking(false)
    }

    fun shutdown() {
        LeoRealtimeTurnBus.unregisterSpeechStopper(realtimeStopper)
        ++notificationEpoch
        ready = false
        currentPrefix = null
        currentUtterance = null
        LeoVoiceDiagnostics.recordSpeechCancelled()
        tts.stop()
        tts.shutdown()
        notifySpeaking(false)
    }
}
