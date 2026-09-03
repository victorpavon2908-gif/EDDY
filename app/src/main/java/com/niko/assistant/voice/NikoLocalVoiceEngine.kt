package com.niko.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.MicrophoneDirection
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
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiser
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserGtcrnModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeechDenoiserModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Núcleo local de voz de LEO.
 *
 * PASSIVE combina KWS + Silero/Canary. ACTIVE usa VAD + GTCRN + Canary y Whisper opcional.
 * Mientras LEO habla, el micrófono NO se apaga: AEC + KWS siguen buscando únicamente "Leo"
 * para permitir barge-in. Durante la orden, Canary publica previews locales periódicos para que
 * la interfaz muestre lo que está entendiendo antes de que termine la frase.
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
    @Volatile private var reopenFollowUp = false
    @Volatile private var conversationAuthorized = false
    @Volatile private var resetRequested = false
    @Volatile private var suppressUntil = 0L
    @Volatile private var speaking = false
    @Volatile private var speakingKwsResetRequested = false
    @Volatile private var bargeInGuardUntil = 0L
    @Volatile private var state = State.STOPPED
    @Volatile private var lastWakeAt = 0L
    @Volatile private var lastPassiveWakeProbeAt = 0L
    @Volatile private var commandSpeechNotified = false
    @Volatile private var commandSpeechFrames = 0
    @Volatile private var lastLivePreviewAt = 0L
    @Volatile private var ownerAuthorizedUntil = 0L

    private var enrolling = false
    private var wakeAcknowledged = false
    private var emptyRecognitionRetries = 0
    private val preRoll = PcmPreRoll(SAMPLE_RATE * 2)
    private val commandAudio = SpeechAudioHistory()
    private val emotionEngine = NikoEmotionEngine(context)
    private val commandWindow = WakeCommandWindow(FIRST_COMMAND_MS, FOLLOW_UP_MS)

    private val previewRunning = AtomicBoolean(false)
    private val previewExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LEO-LiveTranscript").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }
    private var livePreviewSamples = FloatArray(0)

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
    private var passiveWakeVad: Vad? = null
    private var speechDenoiser: OfflineSpeechDenoiser? = null
    private var speakerExtractor: SpeakerEmbeddingExtractor? = null
    private var recognizer: OfflineRecognizer? = null
    private var whisperRecognizer: OfflineRecognizer? = null

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
                worker = thread(name = "LEO-ProVoice", isDaemon = true) { audioLoop() }
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
        conversationAuthorized = false
        ownerAuthorizedUntil = 0L
        reopenFollowUp = false
        LeoRealtimeTurnBus.clearTranscript()
        synchronized(lifecycleLock) {
            stopRequested = true
            running.set(false)
            runCatching { recorder?.stop() }
            worker?.interrupt()
            if (!initializing && worker == null) releaseResources()
        }
    }

    fun stopAndAwait(timeoutMillis: Long = 5_000L): Boolean {
        stop()
        return released.await(timeoutMillis, TimeUnit.MILLISECONDS)
    }

    fun setAssistantBusy(value: Boolean) {
        if (value && !assistantBusy) speakingKwsResetRequested = true
        assistantBusy = value
    }

    fun cancelConversation() {
        conversationAuthorized = false
        ownerAuthorizedUntil = 0L
        reopenFollowUp = false
        resumeAfterSpeech = false
        assistantBusy = false
        speaking = false
        bargeInGuardUntil = 0L
        resetRequested = true
        LeoRealtimeTurnBus.clearTranscript()
    }

    fun setAssistantSpeaking(value: Boolean, continueCommand: Boolean = false) {
        resumeAfterSpeech = continueCommand
        speaking = value
        if (value) {
            speakingKwsResetRequested = true
            LeoRealtimeTurnBus.clearTranscript()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now < bargeInGuardUntil) {
            suppressUntil = 0L
            resumeAfterSpeech = false
            return
        }
        suppressUntil = now + 350L
        resetRequested = true
    }

    fun finishTurn() {
        assistantBusy = false
        speaking = false
        reopenFollowUp = conversationAuthorized
        resumeAfterSpeech = false
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
            initDenoiser()
            initSpanishAsr()
            initWhisperAsrSoft()
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
            NikoKeywordConfig.create(models.modelDir(NikoModelCatalog.keyword), File(context.filesDir, "voice-config"))
        }
        initStage("activación LEO", NikoModelCatalog.keyword) {
            keywordSpotter = KeywordSpotter(config = config)
            keywordStream = keywordSpotter?.createStream()
            check(keywordStream != null)
        }
    }

    private fun initVad() = initStage("detección de voz", NikoModelCatalog.vad) {
        val model = models.file(NikoModelCatalog.vad).absolutePath
        vad = createVad(model, 0.24f, 1.05f, 0.06f, 45f)
        passiveWakeVad = createVad(model, 0.25f, 0.24f, 0.06f, 3.2f)
    }

    private fun createVad(
        model: String,
        threshold: Float,
        minSilenceDuration: Float,
        minSpeechDuration: Float,
        maxSpeechDuration: Float,
    ): Vad = Vad(
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = model,
                threshold = threshold,
                minSilenceDuration = minSilenceDuration,
                minSpeechDuration = minSpeechDuration,
                windowSize = 512,
                maxSpeechDuration = maxSpeechDuration,
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
        ),
    )

    private fun initDenoiser() = initStage("limpieza de voz GTCRN", NikoModelCatalog.denoiser) {
        speechDenoiser = OfflineSpeechDenoiser(
            config = OfflineSpeechDenoiserConfig(
                model = OfflineSpeechDenoiserModelConfig(
                    gtcrn = OfflineSpeechDenoiserGtcrnModelConfig(model = models.file(NikoModelCatalog.denoiser).absolutePath),
                    numThreads = 1,
                    provider = "cpu",
                ),
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

    private fun initWhisperAsrSoft() {
        if (!models.isInstalled(NikoModelCatalog.whisperAsr)) return
        val root = File(models.modelDir(NikoModelCatalog.whisperAsr), WHISPER_DIR)
        whisperRecognizer = runCatching {
            OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = File(root, "tiny-encoder.int8.onnx").absolutePath,
                            decoder = File(root, "tiny-decoder.int8.onnx").absolutePath,
                            language = "es",
                            task = "transcribe",
                        ),
                        tokens = File(root, "tiny-tokens.txt").absolutePath,
                        numThreads = profile.inferenceThreads.coerceIn(1, 2),
                        provider = "cpu",
                    ),
                    decodingMethod = "greedy_search",
                ),
            )
        }.getOrNull()
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
        val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0)
        @Suppress("MissingPermission")
        recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum * 6, SAMPLE_RATE * 2 * 4),
        )
        val localRecorder = recorder
        check(localRecorder?.state == AudioRecord.STATE_INITIALIZED)
        runCatching { localRecorder.setPreferredMicrophoneDirection(MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER) }
        runCatching { localRecorder.setPreferredMicrophoneFieldDimension(0.7f) }
        initializeAudioEffects(localRecorder.audioSessionId)
        true
    }.getOrElse {
        releaseAudioEffects()
        runCatching { recorder?.release() }
        recorder = null
        lastInitializationFailure = InitializationFailure("micrófono", null, it.message ?: it.javaClass.simpleName)
        onError("No pude abrir el micrófono local de LEO.")
        false
    }

    private fun initializeAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()
        if (NoiseSuppressor.isAvailable()) noiseSuppressor = runCatching { NoiseSuppressor.create(audioSessionId) }.getOrNull()?.also { runCatching { it.enabled = true } }
        if (AutomaticGainControl.isAvailable()) automaticGainControl = runCatching { AutomaticGainControl.create(audioSessionId) }.getOrNull()?.also { runCatching { it.enabled = false } }
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
                    val silenced = recorder?.activeRecordingConfiguration?.isClientSilenced == true || audioManager?.isMicrophoneMute == true
                    if (silenced != isMicrophoneSilenced) {
                        isMicrophoneSilenced = silenced
                        conversationAuthorized = false
                        ownerAuthorizedUntil = 0L
                        reopenFollowUp = false
                        commandWindow.close()
                        resetCommandDetector()
                        passiveWakeVad?.reset()
                        resetKeywordStream()
                        preRoll.clear()
                        clearLivePreview()
                        onMicrophoneSilenced(silenced)
                    }
                }
                if (isMicrophoneSilenced) continue
                val enrollmentNow = ownerVoice.enrollmentActive
                if (enrollmentNow != enrolling) {
                    enrolling = enrollmentNow
                    conversationAuthorized = false
                    ownerAuthorizedUntil = 0L
                    reopenFollowUp = false
                    resumeAfterSpeech = false
                    resetCommandDetector()
                    passiveWakeVad?.reset()
                    resetKeywordStream()
                    preRoll.clear()
                    clearLivePreview()
                    if (enrolling) commandWindow.onWake(now) else commandWindow.close()
                }
                if (enrolling && !speaking && !isActive()) commandWindow.onWake(now)

                if (resetRequested) {
                    resetRequested = false
                    resetCommandDetector()
                    passiveWakeVad?.reset()
                    resetKeywordStream()
                    preRoll.clear()
                    clearLivePreview()
                    val resetAt = SystemClock.elapsedRealtime()
                    when {
                        resumeAfterSpeech -> commandWindow.continueAfterPrompt(resetAt)
                        reopenFollowUp && conversationAuthorized -> commandWindow.openFollowUp(resetAt)
                        else -> commandWindow.close()
                    }
                    resumeAfterSpeech = false
                    reopenFollowUp = false
                    commandSpeechNotified = false
                    commandSpeechFrames = 0
                    if (commandWindow.isOpen(resetAt)) setState(State.ACTIVE)
                }

                val raw = FloatArray(count) { buffer[it] / 32768.0f }
                val active = isActive()
                val samples = FarFieldAudioEnhancer.enhance(raw, activeCommand = active)

                if (speaking || assistantBusy) {
                    if (speakingKwsResetRequested) {
                        speakingKwsResetRequested = false
                        resetKeywordStream()
                        preRoll.clear()
                    }
                    processBargeInWake(samples)
                    continue
                }
                if (now < suppressUntil) continue

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

    private fun processBargeInWake(samples: FloatArray) {
        preRoll.append(samples)
        val spotter = keywordSpotter ?: return
        val stream = keywordStream ?: return
        stream.acceptWaveform(samples, SAMPLE_RATE)
        while (spotter.isReady(stream)) spotter.decode(stream)
        if (spotter.getResult(stream).keyword.isBlank()) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeAt < BARGE_IN_DEBOUNCE_MS) {
            resetKeywordStream()
            return
        }

        if (ownerVoice.enrollmentActive || !acceptSpeaker(preRoll.snapshot(), allowRecentOwner = false)) {
            resetKeywordStream()
            preRoll.clear()
            return
        }

        bargeInGuardUntil = now + BARGE_IN_GUARD_MS
        speaking = false
        assistantBusy = false
        suppressUntil = 0L
        LeoRealtimeTurnBus.interruptTurn()
        activateWakeFromPassive(now, includePreRoll = true, debounceMs = BARGE_IN_DEBOUNCE_MS, source = "Sherpa KWS · barge-in")
    }

    private fun processPassiveWake(samples: FloatArray) {
        if (state != State.PASSIVE) {
            conversationAuthorized = false
            reopenFollowUp = false
            resetCommandDetector()
            passiveWakeVad?.reset()
            resetKeywordStream()
            preRoll.clear()
            clearLivePreview()
            setState(State.PASSIVE)
        }
        preRoll.append(samples)
        val spotter = keywordSpotter
        val stream = keywordStream
        if (spotter != null && stream != null) {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            while (spotter.isReady(stream)) spotter.decode(stream)
            if (spotter.getResult(stream).keyword.isNotBlank()) {
                val wakeAudio = preRoll.snapshot()
                if (ownerVoice.enrollmentActive || !acceptSpeaker(wakeAudio, allowRecentOwner = false)) {
                    resetKeywordStream()
                    preRoll.clear()
                    return
                }
                activateWakeFromPassive(SystemClock.elapsedRealtime(), includePreRoll = true, source = "Sherpa KWS")
                return
            }
        }
        processPassiveCanaryWake(samples)
    }

    private fun processPassiveCanaryWake(samples: FloatArray) {
        val localVad = passiveWakeVad ?: return
        val localRecognizer = recognizer ?: return
        localVad.acceptWaveform(samples)
        while (!localVad.empty()) {
            val segment = localVad.front()
            localVad.pop()
            val speech = segment.samples
            val now = SystemClock.elapsedRealtime()
            if (!LeoPassiveWakeVerifier.shouldProbe(speech.size, now, lastPassiveWakeProbeAt)) continue
            lastPassiveWakeProbeAt = now
            if (enrolling) {
                handleEnrollment(speech)
                return
            }
            if (!acceptSpeaker(speech, allowRecentOwner = false)) continue
            val transcript = runCatching { transcribeWith(localRecognizer, speech) }.getOrDefault("")
            when (val wake = LeoPassiveWakeVerifier.consumeTranscript(transcript, now)) {
                WakeResult.Ignored -> Unit
                WakeResult.Activated -> if (activateWakeFromPassive(now, includePreRoll = false, source = "Canary wake verifier")) return
                is WakeResult.Command -> {
                    if (!activateWakeFromPassive(now, includePreRoll = false, source = "Canary wake verifier")) return
                    LeoRealtimeTurnBus.updateTranscript(wake.text)
                    feedCommandDetector(speech)
                    return
                }
            }
        }
    }

    private fun activateWakeFromPassive(
        now: Long,
        includePreRoll: Boolean,
        debounceMs: Long = WAKE_DEBOUNCE_MS,
        source: String = "KWS/Canary local",
    ): Boolean {
        if (now - lastWakeAt < debounceMs) {
            resetKeywordStream()
            passiveWakeVad?.reset()
            return false
        }
        lastWakeAt = now
        conversationAuthorized = true
        reopenFollowUp = false
        wakeAcknowledged = false
        emptyRecognitionRetries = 0
        commandSpeechNotified = false
        commandSpeechFrames = 0
        clearLivePreview()
        resetKeywordStream()
        resetCommandDetector()
        passiveWakeVad?.reset()
        commandWindow.onWake(now)
        if (includePreRoll) feedCommandDetector(preRoll.snapshot())
        preRoll.clear()
        setState(State.ACTIVE)
        LeoVoiceDiagnostics.recordWake(source)
        onWake(1f, ownerVoice.hasProfile())
        return true
    }

    private fun processActive(samples: FloatArray) {
        if (!isActive()) {
            conversationAuthorized = false
            reopenFollowUp = false
            commandWindow.close()
            resetCommandDetector()
            passiveWakeVad?.reset()
            resetKeywordStream()
            clearLivePreview()
            setState(State.PASSIVE)
            return
        }

        detectCommandContinuation(samples)
        updateLivePreview(samples)
        val localVad = vad ?: return
        feedCommandDetector(samples)
        if (localVad.isSpeechDetected()) commandWindow.onSpeech(SystemClock.elapsedRealtime())

        while (!localVad.empty()) {
            val segment = localVad.front()
            localVad.pop()
            if (segment.samples.size < MIN_COMMAND_SAMPLES) continue
            val speech = segment.samples

            if (enrolling) {
                handleEnrollment(speech)
                return
            }
            if (!acceptSpeaker(speech)) {
                clearLivePreview()
                resetCommandDetector()
                setState(State.ACTIVE)
                onUnauthorizedVoice()
                return
            }
            emotionEngine.observeSpeech(speech, SAMPLE_RATE)
            setState(State.PROCESSING)
            val recognition = transcribe(commandAudio.withContext(segment.start, speech))
            val transcript = recognition.text
            if (transcript.isNotBlank()) LeoRealtimeTurnBus.updateTranscript(transcript)
            val text = when (val result = WakeWordGate().consume(transcript)) {
                WakeResult.Activated -> ""
                is WakeResult.Command -> result.text
                WakeResult.Ignored -> transcript
            }

            if (text.isNotBlank()) {
                LeoRealtimeTurnBus.updateTranscript(text)
                if (endsConversation(text)) conversationAuthorized = false
                commandWindow.close()
                assistantBusy = true
                resetCommandDetector()
                onCommand(text)
                return
            }

            if (transcript.isBlank()) {
                clearLivePreview()
                val retry = emptyRecognitionRetries++ == 0
                if (!retry) conversationAuthorized = false
                speaking = true
                resetCommandDetector()
                val prompt = when {
                    !retry -> "No alcancé a entenderte. Volvé a decir LEO."
                    recognition.needsClarification -> "Escuché dos versiones distintas. Repetí la orden para no cambiar lo que dijiste."
                    else -> "No te entendí. ¿Lo repetís?"
                }
                onAwaitingCommand(prompt, retry)
                return
            }

            if (!wakeAcknowledged) {
                wakeAcknowledged = true
                speaking = true
                resetCommandDetector()
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
                LeoRealtimeTurnBus.updateTranscript("Escuchando…")
                onCommandSpeechStarted()
            }
        } else commandSpeechFrames = 0
    }

    private fun updateLivePreview(samples: FloatArray) {
        if (ownerVoice.ownerOnly || ownerVoice.enrollmentActive || !commandSpeechNotified || samples.isEmpty()) return
        appendLiveSamples(samples)
        val now = SystemClock.elapsedRealtime()
        if (livePreviewSamples.size < LIVE_PREVIEW_MIN_SAMPLES || now - lastLivePreviewAt < LIVE_PREVIEW_INTERVAL_MS) return
        if (!previewRunning.compareAndSet(false, true)) return
        lastLivePreviewAt = now
        val snapshot = livePreviewSamples.copyOf()
        val previewToken = LeoRealtimeTurnBus.previewToken()
        val localRecognizer = recognizer ?: run { previewRunning.set(false); return }
        previewExecutor.execute {
            try {
                val text = runCatching { transcribeWith(localRecognizer, snapshot) }.getOrDefault("")
                if (text.isNotBlank() && running.get() && state == State.ACTIVE && !resetRequested) {
                    LeoRealtimeTurnBus.updatePreview(text, previewToken)
                }
            } finally {
                previewRunning.set(false)
            }
        }
    }

    private fun appendLiveSamples(samples: FloatArray) {
        val max = LIVE_PREVIEW_MAX_SAMPLES
        val existing = livePreviewSamples
        val total = existing.size + samples.size
        if (total <= max) {
            livePreviewSamples = FloatArray(total).also {
                existing.copyInto(it, 0)
                samples.copyInto(it, existing.size)
            }
            return
        }
        val keepOld = (max - samples.size).coerceAtLeast(0)
        livePreviewSamples = FloatArray(max).also { target ->
            if (keepOld > 0) existing.copyInto(target, 0, (existing.size - keepOld).coerceAtLeast(0), existing.size)
            samples.copyInto(target, keepOld, (samples.size - (max - keepOld)).coerceAtLeast(0), samples.size)
        }
    }

    private fun clearLivePreview() {
        livePreviewSamples = FloatArray(0)
        lastLivePreviewAt = 0L
        LeoRealtimeTurnBus.clearTranscript()
    }

    private fun resetCommandDetector() {
        vad?.reset()
        commandAudio.clear()
    }

    private fun feedCommandDetector(samples: FloatArray) {
        val detector = vad ?: return
        commandAudio.feed(samples, detector::acceptWaveform)
    }

    private fun transcribe(samples: FloatArray): FaithfulSpeechTranscriber.Result {
        val canary = checkNotNull(recognizer) { "El reconocimiento español Canary no está disponible." }
        val alternate: ((FloatArray) -> String)? = whisperRecognizer?.let { whisper ->
            { audio -> transcribeWith(whisper, audio) }
        }
        return FaithfulSpeechTranscriber(
            primaryDecoder = { transcribeWith(canary, it) },
            alternateDecoder = alternate,
            denoiser = { speechDenoiser?.run(it, SAMPLE_RATE)?.samples },
        ).transcribe(samples)
    }

    private fun transcribeWith(asr: OfflineRecognizer, samples: FloatArray): String = synchronized(asr) {
        val stream = asr.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            asr.decode(stream)
            normalizeTranscript(asr.getResult(stream).text)
        } finally {
            stream.release()
        }
    }

    private fun embedding(samples: FloatArray): FloatArray? {
        val extractor = speakerExtractor ?: return null
        return runCatching {
            val stream = extractor.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                stream.inputFinished()
                if (extractor.isReady(stream)) extractor.compute(stream).takeIf(OwnerVoicePolicy::valid) else null
            } finally { stream.release() }
        }.getOrNull()
    }

    /**
     * Verify the whole turn plus useful windows. A recent confirmed owner may authorize a very
     * short follow-up ("sí", "no", "abrilo") that is too small for a stable speaker embedding.
     */
    private fun acceptSpeaker(samples: FloatArray, allowRecentOwner: Boolean = true): Boolean {
        if (!ownerVoice.ownerOnly) return true
        val now = SystemClock.elapsedRealtime()
        if (samples.size < MIN_SPEAKER_SAMPLES) {
            val recent = allowRecentOwner && now <= ownerAuthorizedUntil
            if (!recent) LeoVoiceDiagnostics.recordOwnerMatch(0f, accepted = false, enabled = true)
            return recent
        }

        val whole = embedding(samples)
        if (whole == null) {
            val recent = allowRecentOwner && now <= ownerAuthorizedUntil && samples.size < SAMPLE_RATE
            if (!recent) LeoVoiceDiagnostics.recordOwnerMatch(0f, accepted = false, enabled = true)
            return recent
        }

        val vectors = mutableListOf(whole)
        val window = SAMPLE_RATE * 3 / 2
        if (samples.size > window * 2) {
            var offset = 0
            val usefulRms = maxOf(0.0022, LeoVoiceDiagnostics.currentNoiseRms().toDouble() * 1.10)
            while (offset < samples.size) {
                val start = minOf(offset, samples.size - window)
                val segment = samples.copyOfRange(start, minOf(start + window, samples.size))
                val rms = sqrt(segment.sumOf { it.toDouble() * it } / segment.size)
                if (rms >= usefulRms) embedding(segment)?.let(vectors::add)
                offset += window
            }
        }

        val accepted = ownerVoice.accepts(vectors)
        if (accepted) ownerAuthorizedUntil = now + OWNER_RECENT_MATCH_MS else ownerAuthorizedUntil = 0L
        return accepted
    }

    private fun handleEnrollment(samples: FloatArray) {
        if (!ownerVoice.enrollmentActive) {
            commandWindow.close()
            resetCommandDetector()
            clearLivePreview()
            setState(State.PASSIVE)
            return
        }
        if (speakerExtractor == null) initSpeakerIdSoft()
        val enough = samples.size >= SAMPLE_RATE * OwnerVoicePolicy.MIN_SAMPLE_SECONDS
        val vector = if (enough) embedding(samples) else null
        val accepted = vector?.let(ownerVoice::enroll) == true
        val prompt = when {
            speakerExtractor == null -> "Falta preparar el reconocimiento de mi voz desde Ajustes."
            !enough -> "Decí una frase de al menos dos segundos, hablando solo vos."
            !accepted -> "No distinguí una voz consistente. Repetí la frase sin otra persona hablando."
            ownerVoice.enrollmentActive -> "Frase ${ownerVoice.enrollmentCount} guardada. Decí otra frase distinta."
            else -> "Tu voz quedó registrada. Ya puedo priorizarla. Decí Leo para empezar."
        }
        conversationAuthorized = false
        ownerAuthorizedUntil = 0L
        commandWindow.close()
        resetCommandDetector()
        passiveWakeVad?.reset()
        clearLivePreview()
        speaking = true
        onAwaitingCommand(prompt, ownerVoice.enrollmentActive)
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

    private fun endsConversation(text: String): Boolean {
        val value = text.lowercase().replace(Regex("[¿?¡!.,;:]"), " ").replace(Regex("\\s+"), " ").trim()
        return value.startsWith("gracias") || value in setOf(
            "eso es todo", "eso era todo", "hasta luego", "nos vemos", "dormite", "dormi", "dormí",
            "descansa", "descansa leo", "descansá", "ya esta", "ya está", "terminamos", "eso seria", "eso sería",
        )
    }

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
        runCatching { passiveWakeVad?.release() }; passiveWakeVad = null
        runCatching { speechDenoiser?.release() }; speechDenoiser = null
        runCatching { speakerExtractor?.release() }; speakerExtractor = null
        runCatching { recognizer?.release() }; recognizer = null
        runCatching { whisperRecognizer?.release() }; whisperRecognizer = null
    }

    private fun releaseResources() {
        conversationAuthorized = false
        ownerAuthorizedUntil = 0L
        reopenFollowUp = false
        clearLivePreview()
        previewExecutor.shutdownNow()
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
        private const val FOLLOW_UP_MS = 20_000L
        private const val WAKE_DEBOUNCE_MS = 900L
        private const val BARGE_IN_DEBOUNCE_MS = 450L
        private const val BARGE_IN_GUARD_MS = 1_400L
        private const val POST_WAKE_GUARD_MS = 150L
        private const val COMMAND_CONTINUATION_RMS = 0.0030f
        private const val COMMAND_CONTINUATION_FRAMES = 2
        private const val MIN_COMMAND_SAMPLES = SAMPLE_RATE / 10
        private const val MIN_SPEAKER_SAMPLES = SAMPLE_RATE * 3 / 10
        private const val OWNER_RECENT_MATCH_MS = 20_000L
        private const val LIVE_PREVIEW_INTERVAL_MS = 950L
        private const val LIVE_PREVIEW_MIN_SAMPLES = SAMPLE_RATE * 3 / 4
        private const val LIVE_PREVIEW_MAX_SAMPLES = SAMPLE_RATE * 6
        private const val ASR_DIR = "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8"
        private const val WHISPER_DIR = "sherpa-onnx-whisper-tiny"
    }
}
