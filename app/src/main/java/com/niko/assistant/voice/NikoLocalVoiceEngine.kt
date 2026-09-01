package com.niko.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.niko.assistant.localai.NikoDeviceProfile
import com.niko.assistant.localai.NikoModelCatalog
import com.niko.assistant.localai.NikoModelManager
import com.niko.assistant.localai.NikoModelSpec
import com.niko.assistant.localai.NikoVoiceProfile
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Núcleo PRO local de voz.
 *
 * PASSIVE: un KeywordSpotter streaming pequeño escucha SOLO el patrón fonético de NIKO.
 * No se hace transcripción completa, búsqueda web ni IA antes de la palabra de activación.
 * ACTIVE: después de NIKO, Silero VAD segmenta la orden y Moonshine transcribe en español.
 * Voice ID queda como aprendizaje suave: nunca bloquea silenciosamente una orden válida.
 */
class NikoLocalVoiceEngine(
    private val context: Context,
    private val models: NikoModelManager,
    private val profile: NikoDeviceProfile,
    private val ownerVoice: NikoVoiceProfile,
    private val onState: (State) -> Unit = {},
    private val onWake: (ownerConfidence: Float, enrolled: Boolean) -> Unit,
    private val onCommandSpeechStarted: () -> Unit = {},
    private val onAwaitingCommand: (String, Boolean) -> Unit = { _, _ -> },
    private val onCommand: (String) -> Unit,
    private val onUnauthorizedVoice: () -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onMicrophoneSilenced: (Boolean) -> Unit = {},
) {
    enum class State { PASSIVE, VERIFYING, ACTIVE, PROCESSING, SPEAKING, STOPPED }

    data class InitializationFailure(
        val stage: String,
        val model: NikoModelSpec?,
        val detail: String,
    )

    private class StageFailure(
        val stageName: String,
        val spec: NikoModelSpec?,
        cause: Throwable,
    ) : RuntimeException(cause)

    private val running = AtomicBoolean(false)
    private val lifecycleLock = Any()
    private val released = CountDownLatch(1)
    private var initializing = false
    private var stopRequested = false
    @Volatile private var assistantBusy = false
    @Volatile private var resumeAfterSpeech = false
    @Volatile private var resetRequested = false
    @Volatile private var suppressUntil = 0L
    private val preRoll = PcmPreRoll(SAMPLE_RATE * 4 / 5)
    private val emotionEngine = NikoEmotionEngine(context)

    @Volatile private var speaking = false
    private val commandWindow = WakeCommandWindow(FIRST_COMMAND_MS)
    @Volatile private var state = State.STOPPED
    @Volatile private var lastWakeAt = 0L
    private var wakeAcknowledged = false
    private var emptyRecognitionRetries = 0
    @Volatile private var commandSpeechNotified = false
    @Volatile private var commandSpeechFrames = 0

    @Volatile
    var lastInitializationFailure: InitializationFailure? = null
        private set

    @Volatile
    var isMicrophoneSilenced: Boolean = false
        private set

    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    private var keywordSpotter: KeywordSpotter? = null
    private var keywordStream: OnlineStream? = null
    private var vad: Vad? = null
    private var speakerExtractor: SpeakerEmbeddingExtractor? = null
    private var recognizer: OfflineRecognizer? = null

    val ready: Boolean get() = models.coreReady()
    val isRunning: Boolean get() = running.get()

    fun start(): Boolean {
        synchronized(lifecycleLock) {
            if (running.get()) return true
            if (initializing || stopRequested) return false
            initializing = true
        }
        try {
            if (!ready) return false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
            if (!initializeModels() || !initializeMicrophone()) return false
            synchronized(lifecycleLock) {
                if (stopRequested) return false
                recorder?.startRecording()
                check(recorder?.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                running.set(true)
                setState(State.PASSIVE)
                worker = thread(name = "NIKO-ProVoice", isDaemon = true) { audioLoop() }
            }
            return true
        } catch (error: RuntimeException) {
            lastInitializationFailure = InitializationFailure("micrófono", null, error.message ?: error.javaClass.simpleName)
            onError("No pude iniciar la captura de audio local.")
            return false
        } finally {
            synchronized(lifecycleLock) {
                initializing = false
                if (worker == null) releaseResources()
            }
        }
    }

    fun stop() {
        synchronized(lifecycleLock) {
            stopRequested = true
            running.set(false)
            runCatching { recorder?.stop() }
            worker?.interrupt()
            if (!initializing && worker == null) releaseResources()
        }
    }

    /** Call off the main thread. A replacement must wait for both AudioRecord and JNI cleanup. */
    fun stopAndAwait(timeoutMillis: Long = 5_000L): Boolean {
        stop()
        return released.await(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    fun setAssistantBusy(value: Boolean) { assistantBusy = value }

    fun setAssistantSpeaking(value: Boolean, continueCommand: Boolean = false) {
        // Do not call VAD/KWS JNI here: this callback normally runs on the UI thread.
        resumeAfterSpeech = continueCommand
        speaking = value
        if (!value) {
            suppressUntil = SystemClock.elapsedRealtime() + 350L
            resetRequested = true
        }
    }

    fun finishTurn() {
        resumeAfterSpeech = false
        assistantBusy = false
        speaking = false
        resetRequested = true
    }

    private fun releaseResources() {
        releaseAudioEffects()
        runCatching { recorder?.release() }
        recorder = null
        releaseModels()
        released.countDown()
        setState(State.STOPPED)
    }

    private inline fun <T> initStage(name: String, spec: NikoModelSpec?, block: () -> T): T = try {
        block()
    } catch (error: Throwable) {
        throw StageFailure(name, spec, error)
    }

    private fun initializeModels(): Boolean {
        lastInitializationFailure = null
        return try {
            initKeywordSpotter()
            initVad()
            initSpanishAsr()
            initSpeakerIdSoft()
            true
        } catch (failure: StageFailure) {
            releaseModels()
            val cause = failure.cause
            lastInitializationFailure = InitializationFailure(
                failure.stageName,
                failure.spec,
                cause?.message ?: cause?.javaClass?.simpleName ?: "error desconocido",
            )
            onError("El módulo local ${failure.stageName} no pudo iniciar. Voy a intentar reparar ese módulo.")
            false
        } catch (error: Throwable) {
            releaseModels()
            lastInitializationFailure = InitializationFailure("núcleo", null, error.message ?: error.javaClass.simpleName)
            onError("No pude iniciar el núcleo PRO de voz. Voy a intentar recuperarlo.")
            false
        }
    }

    private fun initKeywordSpotter() {
        // Configuration/storage failures must not trigger a download of healthy model weights.
        val config = initStage("configuración de activación", null) {
            NikoKeywordConfig.create(models.modelDir(NikoModelCatalog.keyword), File(context.filesDir, "voice-config"))
        }
        initStage("activación NIKO", NikoModelCatalog.keyword) {
            keywordSpotter = KeywordSpotter(config = config)
            keywordStream = keywordSpotter?.createStream()
            check(keywordStream != null)
        }
    }

    private fun initVad() = initStage("detección de voz", NikoModelCatalog.vad) {
        vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = models.file(NikoModelCatalog.vad).absolutePath,
                    threshold = 0.40f,
                    minSilenceDuration = 1.0f,
                    minSpeechDuration = 0.18f,
                    windowSize = 512,
                    maxSpeechDuration = 25f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            ),
        )
    }

    private fun initSpanishAsr() = initStage("reconocimiento español", NikoModelCatalog.spanishAsr) {
        val root = File(models.modelDir(NikoModelCatalog.spanishAsr), ASR_DIR)
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        encoder = File(root, "encoder_model.ort").absolutePath,
                        mergedDecoder = File(root, "decoder_model_merged.ort").absolutePath,
                    ),
                    tokens = File(root, "tokens.txt").absolutePath,
                    numThreads = profile.inferenceThreads.coerceIn(1, 2),
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            ),
        )
    }

    private fun initSpeakerIdSoft() {
        if (!models.isInstalled(NikoModelCatalog.speaker)) return
        speakerExtractor = runCatching {
            SpeakerEmbeddingExtractor(
                config = SpeakerEmbeddingExtractorConfig(
                    model = models.file(NikoModelCatalog.speaker).absolutePath,
                    numThreads = 1,
                    provider = "cpu",
                ),
            )
        }.getOrNull()
    }

    private fun initializeMicrophone(): Boolean = runCatching {
        val minimum = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimum > 0)
        @Suppress("MissingPermission")
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum * 3, 12_288),
        )
        val localRecorder = recorder
        check(localRecorder?.state == AudioRecord.STATE_INITIALIZED)
        initializeAudioEffects(localRecorder.audioSessionId)
        true
    }.getOrElse {
        releaseAudioEffects()
        runCatching { recorder?.release() }
        recorder = null
        lastInitializationFailure = InitializationFailure("micrófono", null, it.message ?: it.javaClass.simpleName)
        onError("No pude abrir el micrófono local de NIKO.")
        false
    }

    private fun initializeAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()
        if (NoiseSuppressor.isAvailable()) noiseSuppressor = runCatching { NoiseSuppressor.create(audioSessionId) }.getOrNull()?.also { runCatching { it.enabled = true } }
        if (AutomaticGainControl.isAvailable()) automaticGainControl = runCatching { AutomaticGainControl.create(audioSessionId) }.getOrNull()?.also { runCatching { it.enabled = true } }
        if (AcousticEchoCanceler.isAvailable()) acousticEchoCanceler = runCatching { AcousticEchoCanceler.create(audioSessionId) }.getOrNull()?.also { runCatching { it.enabled = true } }
    }

    private fun releaseAudioEffects() {
        runCatching { noiseSuppressor?.release() }
        runCatching { automaticGainControl?.release() }
        runCatching { acousticEchoCanceler?.release() }
        noiseSuppressor = null
        automaticGainControl = null
        acousticEchoCanceler = null
    }

    private fun audioLoop() {
        val buffer = ShortArray(512)
        var failedReads = 0
        var nextCaptureCheck = 0L
        val audioManager = context.getSystemService(AudioManager::class.java)
        try {
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val count = recorder?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: -1
                if (!running.get()) break
                if (count <= 0) {
                    check(++failedReads < 5) { "El micrófono dejó de entregar audio ($count)." }
                    Thread.sleep(40L)
                    continue
                }
                failedReads = 0
                val now = SystemClock.elapsedRealtime()
                if (now >= nextCaptureCheck) {
                    nextCaptureCheck = now + 1_000L
                    // Android can keep delivering zeroes when another app/privacy silences us.
                    // Do not mistake that for a healthy listener or repeatedly reopen the mic.
                    val silenced = recorder?.activeRecordingConfiguration?.isClientSilenced == true || audioManager?.isMicrophoneMute == true
                    if (silenced != isMicrophoneSilenced) {
                        isMicrophoneSilenced = silenced
                        commandWindow.close()
                        vad?.reset()
                        resetKeywordStream()
                        preRoll.clear()
                        onMicrophoneSilenced(silenced)
                    }
                }
                if (isMicrophoneSilenced) continue
                if (resetRequested) {
                    resetRequested = false
                    vad?.reset()
                    resetKeywordStream()
                    preRoll.clear()
                    if (resumeAfterSpeech) commandWindow.continueAfterPrompt(SystemClock.elapsedRealtime()) else commandWindow.close()
                    commandSpeechNotified = false
                    commandSpeechFrames = 0
                }
                // Keep AudioRecord open and drain it while speaking/thinking to avoid stale audio/echo.
                if (speaking || assistantBusy || SystemClock.elapsedRealtime() < suppressUntil) continue
                val samples = FloatArray(count) { buffer[it] / 32768.0f }
                if (isActive()) processActive(samples) else processPassiveWake(samples)
            }
        } catch (error: Exception) {
            if (running.get()) onError("La captura local se interrumpió: ${error.message.orEmpty()}")
        } finally {
            synchronized(lifecycleLock) {
                running.set(false)
                releaseResources()
            }
        }
    }

    private fun processPassiveWake(samples: FloatArray) {
        if (state != State.PASSIVE) {
            vad?.reset()
            resetKeywordStream()
            preRoll.clear()
            setState(State.PASSIVE)
        }
        preRoll.append(samples)
        val spotter = keywordSpotter ?: return
        val stream = keywordStream ?: return
        stream.acceptWaveform(samples, SAMPLE_RATE)
        while (spotter.isReady(stream)) spotter.decode(stream)
        val result = spotter.getResult(stream)
        if (result.keyword.isBlank()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeAt < WAKE_DEBOUNCE_MS) {
            spotter.reset(stream)
            return
        }
        lastWakeAt = now
        wakeAcknowledged = false
        emptyRecognitionRetries = 0
        commandSpeechNotified = false
        commandSpeechFrames = 0
        spotter.reset(stream)
        vad?.reset()
        commandWindow.onWake(now)
        // Preserve the syllables immediately following NIKO while the spotter finalizes.
        vad?.acceptWaveform(preRoll.snapshot())
        preRoll.clear()
        setState(State.ACTIVE)
        onWake(1f, ownerVoice.hasProfile())
    }

    private fun processActive(samples: FloatArray) {
        if (!isActive()) {
            commandWindow.close()
            vad?.reset()
            resetKeywordStream()
            setState(State.PASSIVE)
            return
        }

        detectCommandContinuation(samples)

        val localVad = vad ?: return
        localVad.acceptWaveform(samples)
        while (!localVad.empty()) {
            val segment = localVad.front()
            localVad.pop()
            val speech = segment.samples
            if (speech.size < MIN_COMMAND_SAMPLES) continue
            emotionEngine.observeSpeech(speech, SAMPLE_RATE)
            setState(State.PROCESSING)
            val transcript = transcribe(speech)
            val text = when (val result = WakeWordGate().consume(transcript)) {
                WakeResult.Activated -> ""
                is WakeResult.Command -> result.text
                WakeResult.Ignored -> transcript
            }
            if (text.isNotBlank()) {
                learnOwnerSoftly(speech)
                commandWindow.close()
                assistantBusy = true // Before dispatch: never submit two overlapping commands.
                localVad.reset()
                onCommand(text)
                return
            }
            if (transcript.isBlank()) {
                val retry = emptyRecognitionRetries++ == 0
                speaking = true
                localVad.reset()
                onAwaitingCommand(if (retry) "No te entendí. ¿Lo repetís?" else "No alcancé a entenderte. Volvé a decir NIKO.", retry)
                return
            }
            if (!wakeAcknowledged) {
                // Acknowledge only after the completed utterance contained no command.
                // A timer here would speak over a command still being transcribed.
                wakeAcknowledged = true
                speaking = true
                localVad.reset()
                onAwaitingCommand("Ajá.", true)
                return
            }
            if (!speaking) setState(State.ACTIVE)
        }
    }

    private fun detectCommandContinuation(samples: FloatArray) {
        if (commandSpeechNotified) return
        if (SystemClock.elapsedRealtime() - lastWakeAt < POST_WAKE_GUARD_MS) return
        if (samples.isEmpty()) return

        var energy = 0.0
        for (sample in samples) energy += sample * sample
        val rms = sqrt(energy / samples.size).toFloat()
        if (rms >= COMMAND_CONTINUATION_RMS) {
            commandSpeechFrames++
            if (commandSpeechFrames >= COMMAND_CONTINUATION_FRAMES) {
                commandSpeechNotified = true
                onCommandSpeechStarted()
            }
        } else {
            commandSpeechFrames = 0
        }
    }

    private fun transcribe(samples: FloatArray): String {
        // A native decoding failure must reach the service recovery, not become silent text.
        val asr = checkNotNull(recognizer) { "El reconocimiento español no está disponible." }
        val stream = asr.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            asr.decode(stream)
            normalizeTranscript(asr.getResult(stream).text)
        } finally { stream.release() }
    }

    private fun learnOwnerSoftly(samples: FloatArray) {
        if (samples.size < SAMPLE_RATE / 2) return
        val extractor = speakerExtractor ?: return
        runCatching {
            val stream = extractor.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                stream.inputFinished()
                if (extractor.isReady(stream)) ownerVoice.acceptAndLearn(extractor.compute(stream))
            } finally {
                stream.release()
            }
        }
    }

    private fun resetKeywordStream() {
        val spotter = keywordSpotter ?: return
        val stream = keywordStream ?: return
        runCatching { spotter.reset(stream) }
    }

    private fun normalizeTranscript(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+([,.;:!?])"), "$1")
        .trim()

    private fun isActive(): Boolean = commandWindow.isOpen(SystemClock.elapsedRealtime())

    private fun setState(value: State) {
        if (state == value) return
        state = value
        onState(value)
    }

    private fun releaseModels() {
        runCatching { keywordStream?.release() }; keywordStream = null
        runCatching { keywordSpotter?.release() }; keywordSpotter = null
        runCatching { vad?.release() }; vad = null
        runCatching { speakerExtractor?.release() }; speakerExtractor = null
        runCatching { recognizer?.release() }; recognizer = null
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FIRST_COMMAND_MS = 30_000L
        private const val WAKE_DEBOUNCE_MS = 900L
        private const val POST_WAKE_GUARD_MS = 180L
        private const val COMMAND_CONTINUATION_RMS = 0.011f
        private const val COMMAND_CONTINUATION_FRAMES = 2
        private const val MIN_COMMAND_SAMPLES = SAMPLE_RATE / 10
        private const val ASR_DIR = "sherpa-onnx-moonshine-base-es-quantized-2026-02-27"
    }
}
