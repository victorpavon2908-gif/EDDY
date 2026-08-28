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

/** Voz neural española local. No usa red durante la síntesis. */
class EddyNeuralTextToSpeech(
    private val models: EddyModelManager,
    private val profile: EddyDeviceProfile,
    private val onSpeakingChanged: (Boolean) -> Unit = {},
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var tts: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var closed = false

    val isAvailable: Boolean get() = models.isInstalled(EddyModelCatalog.spanishVoice)

    fun speak(text: String): Boolean {
        if (closed || text.isBlank() || !isAvailable) return false
        scope.launch {
            onSpeakingChanged(true)
            try {
                val engine = tts ?: createEngine() ?: return@launch
                val audio = engine.generate(text.take(2_000), sid = 0, speed = 1.02f)
                play(audio.samples, audio.sampleRate)
            } finally {
                onSpeakingChanged(false)
            }
        }
        return true
    }

    fun stop() {
        runCatching { track?.pause() }
        runCatching { track?.flush() }
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        onSpeakingChanged(false)
    }

    fun shutdown() {
        closed = true
        stop()
        runCatching { tts?.release() }
        tts = null
        scope.cancel()
    }

    private fun createEngine(): OfflineTts? = runCatching {
        val root = File(models.modelDir(EddyModelCatalog.spanishVoice), MODEL_DIR)
        OfflineTts(
            config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(root, "es_ES-miro-high.onnx").absolutePath,
                        tokens = File(root, "tokens.txt").absolutePath,
                        dataDir = File(root, "espeak-ng-data").absolutePath,
                        lengthScale = 1.0f,
                    ),
                    numThreads = profile.inferenceThreads.coerceAtMost(4),
                    provider = "cpu",
                ),
                maxNumSentences = 2,
                silenceScale = 0.15f,
            ),
        ).also { tts = it }
    }.getOrNull()

    private fun play(samples: FloatArray, sampleRate: Int) {
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
        audioTrack.play()
        var offset = 0
        while (offset < pcm.size && track === audioTrack) {
            val written = audioTrack.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING)
            if (written <= 0) break
            offset += written
        }
        runCatching { audioTrack.stop() }
        runCatching { audioTrack.release() }
        if (track === audioTrack) track = null
    }

    companion object {
        private const val MODEL_DIR = "vits-piper-es_ES-miro-high"
    }
}
