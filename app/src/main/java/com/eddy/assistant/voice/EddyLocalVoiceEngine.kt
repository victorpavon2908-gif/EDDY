package com.eddy.assistant.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.eddy.assistant.localai.EddyDeviceProfile
import com.eddy.assistant.localai.EddyModelCatalog
import com.eddy.assistant.localai.EddyModelManager
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Micrófono local-first:
 * PASSIVE: KWS + buffer acústico solamente. NO ASR.
 * ACTIVE: después de detectar EDDY y verificar al propietario, VAD + ASR local.
 * SPEAKING: ignora el micrófono para no escucharse a sí mismo.
 */
class EddyLocalVoiceEngine(
    private val context: Context,
    private val models: EddyModelManager,
    private val profile: EddyDeviceProfile,
    private val ownerVoice: EddyVoiceProfile,
    private val onState: (State) -> Unit = {},
    private val onWake: (ownerConfidence: Float, enrolled: Boolean) -> Unit,
    private val onCommand: (String) -> Unit,
    private val onUnauthorizedVoice: () -> Unit = {},
    private val onError: (String) -> Unit = {},
) {
    enum class State { PASSIVE, VERIFYING, ACTIVE, PROCESSING, SPEAKING, STOPPED }

    private val running = AtomicBoolean(false)
    @Volatile private var speaking = false
    @Volatile private var activeUntil = 0L
    @Volatile private var state = State.STOPPED

    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    private var keywordSpotter: KeywordSpotter? = null
    private var keywordStream: OnlineStream? = null
    private var vad: Vad? = null
    private var speakerExtractor: SpeakerEmbeddingExtractor? = null
    private var recognizer: OfflineRecognizer? = null

    private val rolling = RollingAudio((SAMPLE_RATE * 3.2f).toInt())

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
        worker = thread(name = "EDDY-LocalVoice", isDaemon = true) { audioLoop() }
        return true
    }

    fun stop() {
        running.set(false)
        runCatching { recorder?.stop() }
        worker?.interrupt()
        worker = null
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
            setState(State.SPEAKING)
        } else if (running.get()) {
            // Después de hablar, EDDY deja un turno corto para que el dueño continúe
            // sin repetir el nombre, pero cada segmento vuelve a verificar la voz.
            activeUntil = System.currentTimeMillis() + CONTINUATION_MS
            vad?.reset()
            setState(State.ACTIVE)
        }
    }

    private fun initializeModels(): Boolean = runCatching {
        val kwsRoot = models.modelDir(EddyModelCatalog.keyword)
        val kwsDir = java.io.File(kwsRoot, KWS_DIR)
        val modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = java.io.File(kwsDir, "encoder-epoch-13-avg-2-chunk-8-left-64.int8.onnx").absolutePath,
                decoder = java.io.File(kwsDir, "decoder-epoch-13-avg-2-chunk-8-left-64.onnx").absolutePath,
                joiner = java.io.File(kwsDir, "joiner-epoch-13-avg-2-chunk-8-left-64.int8.onnx").absolutePath,
            ),
            tokens = java.io.File(kwsDir, "tokens.txt").absolutePath,
            numThreads = profile.inferenceThreads,
            provider = "cpu",
        )
        keywordSpotter = KeywordSpotter(
            config = KeywordSpotterConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig,
                keywordsFile = "",
                keywordsScore = 1.65f,
                keywordsThreshold = 0.28f,
                numTrailingBlanks = 1,
            ),
        )
        // CMU phones para la pronunciación inglesa "Eddy".
        keywordStream = keywordSpotter!!.createStream("EH1 D IY0 @EDDY")

        vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = models.file(EddyModelCatalog.vad).absolutePath,
                    threshold = 0.52f,
                    minSilenceDuration = 0.45f,
                    minSpeechDuration = 0.18f,
                    windowSize = 512,
                    maxSpeechDuration = 12f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            ),
        )

        speakerExtractor = SpeakerEmbeddingExtractor(
            config = SpeakerEmbeddingExtractorConfig(
                model = models.file(EddyModelCatalog.speaker).absolutePath,
                numThreads = profile.inferenceThreads.coerceAtMost(2),
                provider = "cpu",
            ),
        )

        val asrRoot = java.io.File(models.modelDir(EddyModelCatalog.spanishAsr), ASR_DIR)
        recognizer = OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    moonshine = OfflineMoonshineModelConfig(
                        encoder = java.io.File(asrRoot, "encoder_model.ort").absolutePath,
                        mergedDecoder = java.io.File(asrRoot, "decoder_model_merged.ort").absolutePath,
                    ),
                    tokens = java.io.File(asrRoot, "tokens.txt").absolutePath,
                    numThreads = profile.inferenceThreads,
                    provider = "cpu",
                    modelType = "moonshine",
                ),
            ),
        )
        true
    }.getOrElse {
        releaseModels()
        onError("No pude iniciar el núcleo de voz local; usaré el modo compatible mientras reviso los modelos.")
        false
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
            maxOf(minimum * 2, 8192),
        )
        check(recorder?.state == AudioRecord.STATE_INITIALIZED)
        true
    }.getOrElse {
        onError("No pude abrir el micrófono local de EDDY.")
        false
    }

    private fun audioLoop() {
        val buffer = ShortArray(1600) // 100 ms
        while (running.get() && !Thread.currentThread().isInterrupted) {
            val count = runCatching { recorder?.read(buffer, 0, buffer.size) ?: -1 }.getOrDefault(-1)
            if (count <= 0) continue
            val samples = FloatArray(count) { buffer[it] / 32768.0f }
            rolling.push(samples)

            if (speaking) continue

            if (isActive()) {
                processActive(samples)
            } else {
                if (state != State.PASSIVE) setState(State.PASSIVE)
                processWake(samples)
            }
        }
    }

    private fun processWake(samples: FloatArray) {
        val kws = keywordSpotter ?: return
        val stream = keywordStream ?: return
        stream.acceptWaveform(samples, SAMPLE_RATE)
        while (kws.isReady(stream)) {
            kws.decode(stream)
            val result = kws.getResult(stream)
            if (result.keyword.isNotBlank()) {
                kws.reset(stream)
                setState(State.VERIFYING)
                val embedding = embeddingFor(rolling.snapshot())
                val decision = embedding?.let(ownerVoice::acceptAndLearn)
                if (decision?.accepted == true) {
                    activeUntil = System.currentTimeMillis() + FIRST_COMMAND_MS
                    vad?.reset()
                    setState(State.ACTIVE)
                    onWake(decision.similarity, decision.enrolled)
                } else {
                    activeUntil = 0L
                    setState(State.PASSIVE)
                    onUnauthorizedVoice()
                }
            }
        }
    }

    private fun processActive(samples: FloatArray) {
        if (!isActive()) {
            activeUntil = 0L
            vad?.reset()
            setState(State.PASSIVE)
            return
        }
        val localVad = vad ?: return
        localVad.acceptWaveform(samples)
        while (!localVad.empty()) {
            val segment = localVad.front()
            localVad.pop()
            if (segment.samples.size < SAMPLE_RATE / 4) continue
            setState(State.PROCESSING)

            val embedding = embeddingFor(segment.samples)
            val voiceDecision = embedding?.let(ownerVoice::acceptAndLearn)
            if (voiceDecision?.accepted != true) {
                setState(State.ACTIVE)
                onUnauthorizedVoice()
                continue
            }

            val text = transcribe(segment.samples).trim()
            if (text.isNotBlank()) {
                activeUntil = System.currentTimeMillis() + CONVERSATION_MS
                onCommand(text)
            }
            setState(State.ACTIVE)
        }
    }

    private fun transcribe(samples: FloatArray): String {
        val asr = recognizer ?: return ""
        return runCatching {
            val stream = asr.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                asr.decode(stream)
                asr.getResult(stream).text
            } finally {
                stream.release()
            }
        }.getOrDefault("")
    }

    private fun embeddingFor(samples: FloatArray): FloatArray? {
        if (samples.size < SAMPLE_RATE / 2) return null
        val extractor = speakerExtractor ?: return null
        return runCatching {
            val stream = extractor.createStream()
            try {
                stream.acceptWaveform(samples, SAMPLE_RATE)
                stream.inputFinished()
                if (!extractor.isReady(stream)) return@runCatching null
                extractor.compute(stream)
            } finally {
                stream.release()
            }
        }.getOrNull()
    }

    private fun isActive(): Boolean = activeUntil > System.currentTimeMillis()

    private fun setState(value: State) {
        if (state == value) return
        state = value
        onState(value)
    }

    private fun releaseModels() {
        keywordStream?.release(); keywordStream = null
        keywordSpotter?.release(); keywordSpotter = null
        vad?.release(); vad = null
        speakerExtractor?.release(); speakerExtractor = null
        recognizer?.release(); recognizer = null
    }

    private class RollingAudio(private val capacity: Int) {
        private val data = FloatArray(capacity)
        private var size = 0
        private var cursor = 0

        @Synchronized fun push(samples: FloatArray) {
            samples.forEach { value ->
                data[cursor] = value
                cursor = (cursor + 1) % capacity
                if (size < capacity) size++
            }
        }

        @Synchronized fun snapshot(): FloatArray {
            val result = FloatArray(size)
            val start = (cursor - size + capacity) % capacity
            for (i in 0 until size) result[i] = data[(start + i) % capacity]
            return result
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FIRST_COMMAND_MS = 16_000L
        private const val CONVERSATION_MS = 12_000L
        private const val CONTINUATION_MS = 8_000L
        private const val KWS_DIR = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
        private const val ASR_DIR = "sherpa-onnx-moonshine-base-es-quantized-2026-02-27"
    }
}
