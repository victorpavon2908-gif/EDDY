package com.niko.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.OfflineCanaryModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.niko.assistant.localai.NikoDeviceProfile
import com.niko.assistant.localai.NikoModelCatalog
import com.niko.assistant.localai.NikoModelManager
import com.niko.assistant.localai.NikoModelSpec
import com.niko.assistant.localai.NikoVoiceProfile
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Núcleo local de voz de NIKO.
 *
 * PASSIVE: un KWS pequeño escucha la palabra NIKO sin transcribir conversaciones.
 * ACTIVE: Silero VAD captura la orden y NVIDIA NeMo Canary 180M Flash la transcribe
 * en español completamente en el teléfono. La captura usa una ganancia digital suave
 * para mejorar voz distante sin amplificar silencio casi puro.
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
    @Volatile private var speaking = false
    @Volatile private var state = State.STOPPED
    @Volatile private var lastWakeAt = 0L
    @Volatile private var commandSpeechNotified = false
    @Volatile private var commandSpeechFrames = 0

    private var wakeAcknowledged = false
    private var emptyRecognitionRetries = 0
    private val preRoll = PcmPreRoll(SAMPLE_RATE * 4 / 5)
    private val emotionEngine = NikoEmotionEngine(context)
    private val commandWindow = WakeCommandWindow(FIRST_COMMAND_MS)

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

    /** A replacement engine must wait until AudioRecord and JNI objects are released. */
    fun stopAndAwait(timeoutMillis: Long = 5_000L): Boolean {
        stop()
        return released.await(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    fun setAssistantBusy(value: Boolean) {
        assistantBusy = value
    }

    fun setAssistantSpeaking(value: Boolean, continueCommand: Boolean = false) {
        resumeAfterSpeech = continueCommand
        speaking = value
        if (!value) {
            // Do not let the tail of NIKO's own TTS become the next command.
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
            onError("No pude iniciar el núcleo local de voz. Voy a intentar recuperarlo.")
            false
        }
    }

    private fun initKeywordSpotter() {
        val config = initStage("configuración de activación", null) {
            NikoKeywordConfig.create(
                models.modelDir(NikoModelCatalog.keyword),
                File(context.filesDir, "voice-config"),
            )
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
                    // A lower threshold is intentional after wake: distant speech is quieter.
                    threshold = 0.32f,
                    minSilenceDuration = 0.85f,
                    minSpeechDuration = 0.14f,
                    windowSize = 512,
                    maxSpeechDuration = 30f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            ),
        )
    }

    private fun initSpanishAsr() = initStage("reconocimiento español Canary", NikoModelCatalog.spanishAsr) {
        val root = File(models.modelDir(NikoModelCatalog.spanishAsr), ASR_DIR)
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    canary = OfflineCanaryModelConfig(
                        encoder = File(root, "encoder.int8.onnx").absolutePath,
                        decoder = File(root, "decoder.int8.onnx").absolutePath,
                        srcLang = "es",
                        tgtLang = "es",
                        usePnc = true,
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
            // VOICE_RECOGNITION lets compatible phones apply their speech-oriented front end.
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum * 4, 16_384),
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
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = runCatching { NoiseSuppressor.create(audioSessionId) }.getOrNull()
                ?.also { runCatching { it.enabled = true } }
        }
        if (AutomaticGainControl.isAvailable()) {
            automaticGainControl = runCatching { AutomaticGainControl.create(audioSessionId) }.getOrNull()
                ?.also { runCatching { it.enabled = true } }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            acousticEchoCanceler = runCatching { AcousticEchoCanceler.create(audioSessionId) }.getOrNull()
                ?.also { runCatching { it.enabled = true } }
        }
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
                    val silenced = recorder?.activeRecordingConfiguration?.isClientSilenced == true ||
                        audioManager?.isMicrophoneMute == true
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
                    if (resumeAfterSpeech) {
                        commandWindow.continueAfterPrompt(SystemClock.elapsedRealtime())
                    } else {
                        commandWindow.close()
                    }
                    commandSpeechNotified = false
                    commandSpeechFrames = 0
                }

                // Keep draining while NIKO is speaking/thinking so stale audio never accumulates.
                if (speaking || assistantBusy || now < suppressUntil) continue

                val raw = FloatArray(count) { buffer[it] / 32768.0f }
                val active = isActive()
                val samples = FarFieldAudioEnhancer.enhance(raw, activeCommand = active)
                if (active) processActive(samples) else processPassiveWake(samples)
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

        // Keep the speech directly after the wake word; otherwise "Niko abrí..." may lose "abrí".
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
                assistantBusy = true
                localVad.reset()
                onCommand(text)
                return
            }

            if (transcript.isBlank()) {
                val retry = emptyRecognitionRetries++ == 0
                speaking = true
                localVad.reset()
                onAwaitingCommand(
                    if (retry) "No te entendí. ¿Lo repetís?" else "No alcancé a entenderte. Volvé a decir NIKO.",
                    retry,
                )
                return
            }

            if (!wakeAcknowledged) {
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
        val asr = checkNotNull(recognizer) { "El reconocimiento español Canary no está disponible." }
        val stream = asr.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            asr.decode(stream)
            normalizeTranscript(asr.getResult(stream).text)
        } finally {
            stream.release()
        }
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
        runCatching { keywordStream?.release() }
        keywordStream = null
        runCatching { keywordSpotter?.release() }
        keywordSpotter = null
        runCatching { vad?.release() }
        vad = null
        runCatching { speakerExtractor?.release() }
        speakerExtractor = null
        runCatching { recognizer?.release() }
        recognizer = null
    }

    private fun releaseResources() {
        releaseAudioEffects()
        runCatching { recorder?.release() }
        recorder = null
        releaseModels()
        released.countDown()
        setState(State.STOPPED)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FIRST_COMMAND_MS = 30_000L
        private const val WAKE_DEBOUNCE_MS = 900L
        private const val POST_WAKE_GUARD_MS = 180L
        // Lower than before because the active audio has already passed the far-field enhancer.
        private const val COMMAND_CONTINUATION_RMS = 0.0085f
        private const val COMMAND_CONTINUATION_FRAMES = 2
        private const val MIN_COMMAND_SAMPLES = SAMPLE_RATE / 10
        private const val ASR_DIR = "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8"
    }
}
