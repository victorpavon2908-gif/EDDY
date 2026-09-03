package com.niko.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.niko.assistant.localai.NikoDeviceProfile
import com.niko.assistant.localai.NikoModelCatalog
import com.niko.assistant.localai.NikoModelManager
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Voz neural latina local de LEO, interrumpible al volver a llamarlo. */
class NikoNeuralTextToSpeech(
    private val models: NikoModelManager,
    private val profile: NikoDeviceProfile,
    private val onSpeakingChanged: (Boolean) -> Unit = {},
    private val onFailure: (text: String, audioStarted: Boolean) -> Unit = { _, _ -> },
    private val canUseFallback: () -> Boolean = { false },
) {
    private val mutex = Mutex()
    private val playback = SpeechStartGate()
    @Volatile private var prewarming = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var closed = false
    private val realtimeStopper: () -> Unit = { stop() }

    init { LeoRealtimeTurnBus.registerSpeechStopper(realtimeStopper) }

    val isAvailable: Boolean get() = models.isInstalled(NikoModelCatalog.spanishVoice)

    fun prewarm() {
        if (closed || prewarming || !isAvailable) return
        prewarming = true
        scope.launch { mutex.withLock { if (!closed && tts == null) createEngine() } }
    }

    fun speak(text: String, speed: Float = 1.0f): Boolean {
        if (closed || text.isBlank() || !isAvailable) return false
        val token = playback.begin()
        // Covers cold initialization AND waiting behind a canceled native generate().
        val firstAudioDeadline = scope.launch(Dispatchers.Main) {
            delay(2_300L)
            if (!closed && canUseFallback() && playback.expire(token)) onFailure(text, false)
        }
        scope.launch {
            mutex.withLock {
                if (closed || !playback.current(token)) return@withLock
                var failed = false
                var audioStarted = false
                try {
                    val engine = tts ?: createEngine() ?: error("Voz local no disponible")
                    for (chunk in SpeechProsody.chunks(text, 96)) {
                        if (closed || !playback.current(token)) break
                        val audio = engine.generate(
                            com.niko.assistant.ai.NikoIdentity.forSpeech(chunk),
                            sid = 0,
                            speed = speed.coerceIn(0.85f, 1.15f),
                        )
                        if (!closed && playback.current(token)) play(audio.samples, audio.sampleRate, token) { audioStarted = true }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally {
                    if (closed) {
                        runCatching { tts?.release() }
                        tts = null
                    }
                    firstAudioDeadline.cancel()
                    scope.launch(Dispatchers.Main) {
                        if (playback.current(token) && !closed) {
                            if (failed) onFailure(text, audioStarted) else onSpeakingChanged(false)
                        }
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        val token = playback.cancel()
        runCatching { track?.pause() }
        scope.launch(Dispatchers.Main) { if (playback.latest(token)) onSpeakingChanged(false) }
    }

    fun shutdown() {
        LeoRealtimeTurnBus.unregisterSpeechStopper(realtimeStopper)
        closed = true
        stop()
        scope.launch {
            mutex.withLock {
                runCatching { tts?.release() }
                tts = null
            }
            scope.cancel()
        }
    }

    private fun createEngine(): OfflineTts? = runCatching {
        val root = File(models.modelDir(NikoModelCatalog.spanishVoice), MODEL_DIR)
        OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(root, "es_MX-claude-high.onnx").absolutePath,
                        tokens = File(root, "tokens.txt").absolutePath,
                        dataDir = File(root, "espeak-ng-data").absolutePath,
                        lengthScale = 0.96f,
                    ),
                    numThreads = profile.inferenceThreads.coerceAtMost(2),
                    provider = "cpu",
                ),
                maxNumSentences = 1,
                silenceScale = 0.12f,
            ),
        ).also { tts = it }
    }.getOrNull()

    private suspend fun play(samples: FloatArray, sampleRate: Int, token: Long, onAudioQueued: () -> Unit) {
        check(samples.isNotEmpty()) { "La síntesis local no produjo audio" }
        val pcm = ShortArray(samples.size) { index -> (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(4096)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minimum, 16_384))
            .build()
        track = audioTrack
        try {
            if (!playback.markStarted(token) || closed) return
            audioTrack.play()
            var offset = 0
            while (offset < pcm.size && !closed && playback.current(token)) {
                val written = audioTrack.write(pcm, offset, minOf(4_096, pcm.size - offset), AudioTrack.WRITE_BLOCKING)
                check(written > 0) { "No pude reproducir la voz" }
                onAudioQueued()
                offset += written
            }
            val deadline = SystemClock.elapsedRealtime() + (offset.toLong() * 1_000L / sampleRate) + 3_000L
            while (!closed && playback.current(token) && audioTrack.playbackHeadPosition.toLong() < offset && SystemClock.elapsedRealtime() < deadline) delay(20L)
        } finally {
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
            if (track === audioTrack) track = null
        }
    }

    companion object { private const val MODEL_DIR = "vits-piper-es_MX-claude-high" }
}
