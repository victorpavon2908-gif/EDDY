package com.eddy.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import com.eddy.assistant.localai.EddyDeviceProfile
import com.eddy.assistant.localai.EddyModelCatalog
import com.eddy.assistant.localai.EddyModelManager
import com.eddy.assistant.localai.EddyModelSpec
import com.eddy.assistant.localai.EddyVoiceProfile
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Núcleo PRO local de voz.
 *
 * PASSIVE: un KeywordSpotter streaming pequeño escucha SOLO el patrón fonético de EDDY.
 * No se hace transcripción completa, búsqueda web ni IA antes de la palabra de activación.
 * ACTIVE: después de EDDY, Silero VAD segmenta la orden y Moonshine transcribe en español.
 * Voice ID queda como aprendizaje suave: nunca bloquea silenciosamente una orden válida.
 */
class EddyLocalVoiceEngine(
    private val context: Context,
    private val models: EddyModelManager,
    private val profile: EddyDeviceProfile,
    private val ownerVoice: EddyVoiceProfile,
    private val onState: (State) -> Unit = {},
    private val onWake: (ownerConfidence: Float, enrolled: Boolean) -> Unit,
    private val onCommandSpeechStarted: () -> Unit = {},
    private val onCommand: (String) -> Unit,
    private val onUnauthorizedVoice: () -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    enum class State { PASSIVE, VERIFYING, ACTIVE, PROCESSING, SPEAKING, STOPPED }

    data class InitializationFailure(
        val stage: String,
        val model: EddyModelSpec?,
        val detail: String,
    )

    private class StageFailure(
        val stageName: String,
        val spec: EddyModelSpec?,
        cause: Throwable,
    ) : RuntimeException(cause)

    private val running = AtomicBoolean(false)
    private val emotionEngine = EddyEmotionEngine(context)

    @Volatile private var speaking = false
    @Volatile private var activeUntil = 0L
    @Volatile private var state = State.STOPPED
    @Volatile private var lastWakeAt = 0L
    @Volatile private var commandSpeechNotified = false
    @Volatile private var commandSpeechFrames = 0

    @Volatile
    var lastInitializationFailure: InitializationFailure? = null
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

    fun start(): Boolean {
        if (running.get()) return true
        if (!ready) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Necesito permiso de micrófono para activar el núcleo local.")
            return false
        }
        if (!initializeModels()) return false
        if (!initializeMicrophone()) return false

        running.set(true)
        setState(State.PASSIVE)
        recorder?.startRecording()
        worker = thread(name = "EDDY-ProVoice", isDaemon = true) { audioLoop() }
        return true
    }

    fun stop() {
        running.set(false)
        runCatching { recorder?.stop() }
        worker?.interrupt()
        worker = null
        releaseAudioEffects()
        recorder?.release()
        recorder = null
        releaseModels()
        setState(State.STOPPED)
    }

    fun setAssistantSpeaking(value: Boolean) {
        speaking = value
        if (value) {
            activeUntil = 0L
            vad?.reset()
            resetKeywordStream()
            setState(State.SPEAKING)
        } else if (running.get()) {
            activeUntil = System.currentTimeMillis() + CONTINUATION_MS
            vad?.reset()
            commandSpeechNotified = true
            commandSpeechFrames = 0
            setState(State.ACTIVE)
        }
    }

    private inline fun <T> initStage(name: String, spec: EddyModelSpec?, block: () -> T): T = try {
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
            failure.spec?.let(models::invalidate)
            onError("El módulo local ${failure.stageName} no pudo iniciar. EDDY usará escucha compatible mientras se repara.")
            false
        } catch (error: Throwable) {
            releaseModels()
            lastInitializationFailure = InitializationFailure("núcleo", null, error.message ?: error.javaClass.simpleName)
            onError("No pude iniciar el núcleo PRO de voz. EDDY usará escucha compatible.")
            false
        }
    }

    private fun initKeywordSpotter() = initStage("activación EDDY", EddyModelCatalog.keyword) {
        val root = File(models.modelDir(EddyModelCatalog.keyword), KWS_DIR)
        val config = KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(root, KWS_ENCODER).absolutePath,
                    decoder = File(root, KWS_DECODER).absolutePath,
                    joiner = File(root, KWS_JOINER).absolutePath,
                ),
                tokens = File(root, "tokens.txt").absolutePath,
                numThreads = 1,
                provider = "cpu",
                modelType = "zipformer2",
                modelingUnit = "phone+ppinyin",
            ),
            maxActivePaths = 4,
            keywordsFile = "",
            keywordsScore = 3.0f,
            keywordsThreshold = 0.08f,
            numTrailingBlanks = 1,
        )
        keywordSpotter = KeywordSpotter(config = config)
        keywordStream = keywordSpotter?.createStream(EDDY_KEYWORDS)
        check(keywordStream != null)
    }

    private fun initVad() = initStage("detección de voz", EddyModelCatalog.vad) {
        vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = models.file(EddyModelCatalog.vad).absolutePath,
                    threshold = 0.40f,
                    minSilenceDuration = 0.55f,
                    minSpeechDuration = 0.08f,
                    windowSize = 512,
                    maxSpeechDuration = 18f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            ),
        )
    }

    private fun initSpanishAsr() = initStage("reconocimiento español", EddyModelCatalog.spanishAsr) {
        val root = File(models.modelDir(EddyModelCatalog.spanishAsr), ASR_DIR)
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
        if (!models.isInstalled(EddyModelCatalog.speaker)) return
        speakerExtractor = runCatching {
            SpeakerEmbeddingExtractor(
                config = SpeakerEmbeddingExtractorConfig(
                    model = models.file(EddyModelCatalog.speaker).absolutePath,
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
        onError("No pude abrir el micrófono local de EDDY.")
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
        val buffer = ShortArray(800)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            val count = runCatching { recorder?.read(buffer, 0, buffer.size) ?: -1 }.getOrDefault(-1)
            if (count <= 0) continue
            if (speaking) continue
            val samples = FloatArray(count) { buffer[it] / 32768.0f }
            if (isActive()) processActive(samples) else processPassiveWake(samples)
        }
    }

    private fun processPassiveWake(samples: FloatArray) {
        if (state != State.PASSIVE) setState(State.PASSIVE)
        val spotter = keywordSpotter ?: return
        val stream = keywordStream ?: return
        stream.acceptWaveform(samples, SAMPLE_RATE)
        while (spotter.isReady(stream)) spotter.decode(stream)
        val result = spotter.getResult(stream)
        if (result.keyword.isBlank()) return

        val now = System.currentTimeMillis()
        if (now - lastWakeAt < WAKE_DEBOUNCE_MS) {
            spotter.reset(stream)
            return
        }
        lastWakeAt = now
        commandSpeechNotified = false
        commandSpeechFrames = 0
        spotter.reset(stream)
        vad?.reset()
        activeUntil = now + FIRST_COMMAND_MS
        setState(State.ACTIVE)
        onWake(1f, ownerVoice.hasProfile())
    }

    private fun processActive(samples: FloatArray) {
        if (!isActive()) {
            activeUntil = 0L
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
            val text = transcribe(speech)
            if (text.isNotBlank()) {
                learnOwnerSoftly(speech)
                activeUntil = System.currentTimeMillis() + CONVERSATION_MS
                onCommand(text)
            }
            if (!speaking) setState(State.ACTIVE)
        }
    }

    private fun detectCommandContinuation(samples: FloatArray) {
        if (commandSpeechNotified) return
        if (System.currentTimeMillis() - lastWakeAt < POST_WAKE_GUARD_MS) return
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
        val asr = recognizer ?: return ""
        return runCatching {
            val stream = asr.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                asr.decode(stream)
                normalizeTranscript(asr.getResult(stream).text)
            } finally {
                stream.release()
            }
        }.getOrDefault("")
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

    private fun isActive(): Boolean = activeUntil > System.currentTimeMillis()

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
        private const val FIRST_COMMAND_MS = 15_000L
        private const val CONVERSATION_MS = 18_000L
        private const val CONTINUATION_MS = 12_000L
        private const val WAKE_DEBOUNCE_MS = 900L
        private const val POST_WAKE_GUARD_MS = 180L
        private const val COMMAND_CONTINUATION_RMS = 0.011f
        private const val COMMAND_CONTINUATION_FRAMES = 2
        private const val MIN_COMMAND_SAMPLES = SAMPLE_RATE / 10
        private const val ASR_DIR = "sherpa-onnx-moonshine-base-es-quantized-2026-02-27"
        private const val KWS_DIR = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
        private const val KWS_ENCODER = "encoder-epoch-13-avg-2-chunk-8-left-64.int8.onnx"
        private const val KWS_DECODER = "decoder-epoch-13-avg-2-chunk-8-left-64.onnx"
        private const val KWS_JOINER = "joiner-epoch-13-avg-2-chunk-8-left-64.int8.onnx"

        private const val EDDY_KEYWORDS =
            "EH1 D IY0 :3.2 #0.07 @EDDY\n" +
            "EH1 D IY1 :3.2 #0.07 @EDDY\n" +
            "EH0 D IY0 :3.0 #0.08 @EDDY\n" +
            "EH0 D IY1 :3.0 #0.08 @EDDY\n" +
            "EH1 D IH0 :2.8 #0.10 @EDDY\n" +
            "EH1 D :2.0 #0.18 @EDDY_SHORT"
    }
}
