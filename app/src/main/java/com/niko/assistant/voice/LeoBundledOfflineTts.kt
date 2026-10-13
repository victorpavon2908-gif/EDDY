package com.niko.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bundled Spanish offline neural voice. Never downloads or installs anything after APK installation.
 * Heavy model initialization and synthesis stay off the Android main thread.
 */
internal class LeoBundledOfflineTts(
    private val context: Context,
    private val onSpeaking: (Boolean) -> Unit,
) {
    companion object {
        private const val ROOT = "leo-tts/vits-piper-es_ES-davefx-medium"
    }
    private val work = Executors.newSingleThreadExecutor { task ->
        Thread(task, "LEO-offline-tts").apply { isDaemon = true }
    }
    private val epoch = AtomicInteger(0)
    @Volatile private var track: AudioTrack? = null
    @Volatile private var engine: OfflineTts? = null

    val isPackaged: Boolean
        get() = runCatching {
            context.assets.open("$ROOT/es_ES-davefx-medium.onnx").use { it.read() >= 0 }
        }.getOrDefault(false)

    private fun copyAssets(path: String, destination: File) {
        val children = context.assets.list(path).orEmpty()
        if (children.isNotEmpty()) {
            destination.mkdirs()
            children.forEach { name -> copyAssets("$path/$name", File(destination, name)) }
        } else {
            if (destination.exists() && destination.length() > 0) return
            destination.parentFile?.mkdirs()
            context.assets.open(path).use { input ->
                File(destination.parentFile, destination.name + ".tmp").let { temp ->
                    temp.outputStream().use { input.copyTo(it) }
                    check(temp.renameTo(destination)) { "Cannot install bundled TTS asset" }
                }
            }
        }
    }

    private fun obtainEngine(): OfflineTts {
        engine?.let { return it }
        val directory = File(context.noBackupFilesDir, "leo-offline-tts")
        copyAssets(ROOT, directory)
        val model = File(directory, "es_ES-davefx-medium.onnx")
        val tokens = File(directory, "tokens.txt")
        val phonemes = File(directory, "espeak-ng-data")
        check(model.isFile && tokens.isFile && phonemes.isDirectory)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = model.absolutePath,
                    tokens = tokens.absolutePath,
                    dataDir = phonemes.absolutePath,
                ),
                numThreads = 2,
                debug = false,
            ),
            maxNumSentences = 1,
        )
        return OfflineTts(config = config).also { engine = it }
    }

    fun speak(text: String, prosody: SpeechProsody, onFailure: () -> Unit): Boolean {
        if (!isPackaged || text.isBlank()) return false
        val request = epoch.incrementAndGet()
        work.execute {
            try {
                val tts = obtainEngine()
                if (epoch.get() != request) return@execute
                val chunks = SpeechProsody.chunks(text, 220)
                onSpeaking(true)
                for (chunk in chunks) {
                    if (epoch.get() != request) break
                    val audio = tts.generateWithConfigAndCallback(
                        text = chunk,
                        config = GenerationConfig(sid = 0, speed = prosody.speed.coerceIn(0.85f, 1.15f)),
                        callback = { _ -> if (epoch.get() == request) 1 else 0 },
                    )
                    if (epoch.get() != request) break
                    val pcm = ShortArray(audio.samples.size) { index ->
                        (audio.samples[index].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    val minBuffer = AudioTrack.getMinBufferSize(
                        audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    ).coerceAtLeast(4096)
                    val player = AudioTrack(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
                        AudioFormat.Builder().setSampleRate(audio.sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
                        minBuffer, AudioTrack.MODE_STREAM, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
                    )
                    track = player
                    try {
                        player.play()
                        var offset = 0
                        while (offset < pcm.size && epoch.get() == request) {
                            val length = minOf(2048, pcm.size - offset)
                            val written = player.write(pcm, offset, length)
                            if (written <= 0) break
                            offset += written
                        }
                    } finally {
                        runCatching { player.stop() }
                        player.release()
                        if (track === player) track = null
                    }
                }
            } catch (_: Exception) {
                if (epoch.get() == request) onFailure()
            } finally {
                if (epoch.get() == request) onSpeaking(false)
            }
        }
        return true
    }

    fun stop() {
        epoch.incrementAndGet()
        track?.let { runCatching { it.pause(); it.flush() } }
        onSpeaking(false)
    }

    fun shutdown() {
        stop()
        work.shutdownNow()
        runCatching { engine?.release() }
        engine = null
    }
}
