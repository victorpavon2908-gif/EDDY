package com.eddy.assistant.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.eddy.assistant.localai.EddyDeviceProfile
import com.eddy.assistant.localai.EddyModelCatalog
import com.eddy.assistant.localai.EddyModelManager
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.os.SystemClock

/** Voz neural latina local de EDDY. No usa red durante la síntesis. */
class EddyNeuralTextToSpeech(
    private val models: EddyModelManager,
    private val profile: EddyDeviceProfile,
    private val onSpeakingChanged: (Boolean) -> Unit = {},
    private val onFailure: (String) -> Unit = {},
) {
    private val mutex = Mutex()
    @Volatile private var generation = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var closed = false

    val isAvailable: Boolean get() = models.isInstalled(EddyModelCatalog.spanishVoice)

    fun speak(text: String, speed: Float = 1.0f): Boolean {
        if (closed || text.isBlank() || !isAvailable) return false
        val token = ++generation
        scope.launch {
            mutex.withLock {
                if (closed || token != generation) return@withLock
                onSpeakingChanged(true)
                var failed = false
                try {
                    val engine = tts ?: createEngine() ?: error("Voz local no disponible")
                    for (chunk in SpeechProsody.chunks(text, 1_000)) {
                        if (closed || token != generation) break
                        val audio = engine.generate(chunk, sid = 0, speed = speed.coerceIn(0.85f, 1.15f))
                        if (!closed && token == generation) play(audio.samples, audio.sampleRate, token)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    failed = true
                } finally {
                    if (closed) { runCatching { tts?.release() }; tts = null }
                    if (token == generation && !closed) {
                        if (failed) onFailure(text) else onSpeakingChanged(false)
                    }
                }
            }
        }
        return true
    }

    fun stop() {
        ++generation
        runCatching { track?.pause() }
        // AudioTrack and JNI objects are released by their worker, never during write/generate.
        onSpeakingChanged(false)
    }

    fun shutdown() {
        closed = true
        stop()
        scope.launch {
            mutex.withLock { runCatching { tts?.release() }; tts = null }
            scope.cancel()
        }
    }

    private fun createEngine(): OfflineTts? = runCatching {
        val root = File(models.modelDir(EddyModelCatalog.spanishVoice), MODEL_DIR)
        OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(root, "es_MX-claude-high.onnx").absolutePath,
                        tokens = File(root, "tokens.txt").absolutePath,
                        dataDir = File(root, "espeak-ng-data").absolutePath,
                        lengthScale = 0.96f,
                    ),
                    numThreads = profile.inferenceThreads.coerceAtMost(4),
                    provider = "cpu",
                ),
                maxNumSentences = 2,
                silenceScale = 0.12f,
            ),
        ).also { tts = it }
    }.getOrNull()

    private suspend fun play(samples: FloatArray, sampleRate: Int, token: Int) {
        if (samples.isEmpty()) return
        val pcm = ShortArray(samples.size) { index ->
            (samples[index].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()
        }
        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minimum, 16_384))
            .build()
        track = audioTrack
        try {
            audioTrack.play()
            var offset = 0
            while (offset < pcm.size && !closed && token == generation) {
                val written = audioTrack.write(pcm, offset, minOf(4_096, pcm.size - offset), AudioTrack.WRITE_BLOCKING)
                check(written > 0) { "No pude reproducir la voz" }
                offset += written
            }
            // write() only queues audio. Wait for playback so the final words are not cut off.
            val deadline = SystemClock.elapsedRealtime() + (offset.toLong() * 1_000L / sampleRate) + 3_000L
            while (!closed && token == generation && audioTrack.playbackHeadPosition.toLong() < offset && SystemClock.elapsedRealtime() < deadline) {
                delay(20L)
            }
        } finally {
            runCatching { audioTrack.stop() }
            runCatching { audioTrack.release() }
            if (track === audioTrack) track = null
        }
    }

    companion object {
        private const val MODEL_DIR = "vits-piper-es_MX-claude-high"
    }
}
