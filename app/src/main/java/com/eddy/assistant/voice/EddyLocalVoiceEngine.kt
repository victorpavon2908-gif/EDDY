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
import com.eddy.assistant.localai.EddyModelSpec
import com.eddy.assistant.localai.EddyVoiceProfile
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractor
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Núcleo de voz local adaptativo.
 *
 * PASSIVE: Silero VAD espera voz. Solo cuando termina una frase corta, Moonshine (el mismo
 * ASR usado para comandos) comprueba si se dijo "EDDY". No existe un KWS adicional.
 * VERIFYING: CAMPPlus verifica la voz del propietario únicamente si se detectó EDDY.
 * ACTIVE: VAD + Moonshine procesan órdenes y CAMPPlus mantiene Voice ID.
 * SPEAKING: el micrófono ignora el audio para que EDDY no se active a sí mismo.
 *
 * Ventajas frente al KWS anterior:
 * - un modelo menos que descargar/cargar;
 * - elimina incompatibilidades de KeywordSpotter/Transducer entre SoC;
 * - mantiene funcionamiento completamente local;
 * - conserva Voice ID y comandos en una sola frase: "EDDY, abre la cámara".
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
    private val wakeGate = WakeWordGate()

    @Volatile private var speaking = false
    @Volatile private var activeUntil = 0L
    @Volatile private var state = State.STOPPED
    @Volatile private var lastPassiveDecodeAt = 0L

    @Volatile
    var lastInitializationFailure: InitializationFailure? = null
        private set

    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

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

        // EDDY ya no usa el KWS Zipformer. Borramos sus restos al actualizar para recuperar
        // espacio y evitar que un archivo viejo vuelva a participar en reparaciones futuras.
        runCatching { models.invalidate(EddyModelCatalog.keyword) }

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
            wakeGate.disarm()
            vad?.reset()
            setState(State.SPEAKING)
        } else if (running.get()) {
            activeUntil = System.currentTimeMillis() + CONTINUATION_MS
            vad?.reset()
            setState(State.ACTIVE)
        }
    }

    private inline fun <T> initStage(
        name: String,
        spec: EddyModelSpec?,
        block: () -> T,
    ): T = try {
        block()
    } catch (error: Throwable) {
        throw StageFailure(name, spec, error)
    }

    private fun initializeModels(): Boolean {
        lastInitializationFailure = null
        return try {
            initVad()
            initSpanishAsr()
            initSpeakerId()
            true
        } catch (failure: StageFailure) {
            releaseModels()
            val cause = failure.cause
            lastInitializationFailure = InitializationFailure(
                stage = failure.stageName,
                model = failure.spec,
                detail = cause?.message ?: cause?.javaClass?.simpleName ?: "error desconocido",
            )
            failure.spec?.let(models::invalidate)
            onError("El módulo local ${failure.stageName} no pudo iniciar. EDDY intentará repararlo.")
            false
        } catch (error: Throwable) {
            releaseModels()
            lastInitializationFailure = InitializationFailure(
                stage = "núcleo",
                model = null,
                detail = error.message ?: error.javaClass.simpleName,
            )
            onError("No pude iniciar el núcleo de voz local. Revisaré los modelos descargados.")
            false
        }
    }

    private fun initVad() = initStage("detección de voz", EddyModelCatalog.vad) {
        vad = Vad(
            config = VadModelConfig(
                sileroVadModelConfig = SileroVadModelConfig(
                    model = models.file(EddyModelCatalog.vad).absolutePath,
                    threshold = 0.52f,
                    minSilenceDuration = 0.42f,
                    minSpeechDuration = 0.16f,
                    windowSize = 512,
                    maxSpeechDuration = 12f,
                ),
                sampleRate = SAMPLE_RATE,
                numThreads = 1,
                provider = "cpu",
            ),
        )
    }

    private fun initSpanishAsr() = initStage("reconocimiento español", EddyModelCatalog.spanishAsr) {
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
                    // Equipos LITE usan 1 hilo por EddyDeviceProfile; no forzamos paralelismo.
                    numThreads = profile.inferenceThreads.coerceIn(1, 2),
                    provider = "cpu",
                ),
                decodingMethod = "greedy_search",
            ),
        )
    }

    private fun initSpeakerId() = initStage("identificación de voz", EddyModelCatalog.speaker) {
        speakerExtractor = SpeakerEmbeddingExtractor(
            config = SpeakerEmbeddingExtractorConfig(
                model = models.file(EddyModelCatalog.speaker).absolutePath,
                numThreads = profile.inferenceThreads.coerceIn(1, 2),
                provider = "cpu",
            ),
        )
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
        val buffer = ShortArray(1600)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            val count = runCatching { recorder?.read(buffer, 0, buffer.size) ?: -1 }.getOrDefault(-1)
            if (count <= 0) continue
            val samples = FloatArray(count) { buffer[it] / 32768.0f }

            if (speaking) continue

            if (isActive()) {
                processActive(samples)
            } else {
                if (state != State.PASSIVE) setState(State.PASSIVE)
                processPassiveWake(samples)
            }
        }
    }

    /**
     * Activación sin KWS dedicado. Silero entrega una frase completa; Moonshine solo se
     * ejecuta cuando hubo voz real y únicamente para segmentos razonablemente cortos.
     */
    private fun processPassiveWake(samples: FloatArray) {
        val localVad = vad ?: return
        localVad.acceptWaveform(samples)

        while (!localVad.empty()) {
            val segment = localVad.front()
            localVad.pop()
            val speech = segment.samples

            if (speech.size < MIN_WAKE_SAMPLES) continue
            if (speech.size > MAX_WAKE_SCAN_SAMPLES) continue

            val now = System.currentTimeMillis()
            if (now - lastPassiveDecodeAt < PASSIVE_DECODE_COOLDOWN_MS) continue
            lastPassiveDecodeAt = now

            val text = transcribe(speech).trim()
            if (text.isBlank() || !wakeGate.hasWakeWord(text)) continue

            val result = wakeGate.consume(text, now)
            wakeGate.disarm()
            if (result == WakeResult.Ignored) continue

            setState(State.VERIFYING)
            val embedding = embeddingFor(speech)
            val decision = embedding?.let(ownerVoice::acceptAndLearn)
            if (decision?.accepted != true) {
                activeUntil = 0L
                setState(State.PASSIVE)
                onUnauthorizedVoice()
                continue
            }

            onWake(decision.similarity, decision.enrolled)
            vad?.reset()

            when (result) {
                WakeResult.Activated -> {
                    activeUntil = System.currentTimeMillis() + FIRST_COMMAND_MS
                    setState(State.ACTIVE)
                }
                is WakeResult.Command -> {
                    activeUntil = System.currentTimeMillis() + CONVERSATION_MS
                    setState(State.ACTIVE)
                    val command = result.text.trim()
                    if (command.isNotBlank()) onCommand(command)
                }
                WakeResult.Ignored -> Unit
            }
        }
    }

    private fun processActive(samples: FloatArray) {
        if (!isActive()) {
            activeUntil = 0L
            wakeGate.disarm()
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
        vad?.release(); vad = null
        speakerExtractor?.release(); speakerExtractor = null
        recognizer?.release(); recognizer = null
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val FIRST_COMMAND_MS = 16_000L
        private const val CONVERSATION_MS = 12_000L
        private const val CONTINUATION_MS = 8_000L
        private const val PASSIVE_DECODE_COOLDOWN_MS = 250L
        private const val MIN_WAKE_SAMPLES = SAMPLE_RATE / 4
        private const val MAX_WAKE_SCAN_SAMPLES = SAMPLE_RATE * 6
        private const val ASR_DIR = "sherpa-onnx-moonshine-base-es-quantized-2026-02-27"
    }
}
