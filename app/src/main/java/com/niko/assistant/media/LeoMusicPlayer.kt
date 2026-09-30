package com.niko.assistant.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground-only player. Preparation may finish after pause, but must not auto-resume. */
class LeoMusicPlayer(private val context: Context) {
    data class State(val title: String = "", val playing: Boolean = false, val loading: Boolean = false, val error: String = "")
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val manager = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { focusDucked = true; updateVolume() }
                AudioManager.AUDIOFOCUS_GAIN -> { focusDucked = false; updateVolume() }
            }
        }, Handler(Looper.getMainLooper())).build()
    private var player: MediaPlayer? = null
    private var prepared = false
    private var allowPlay = false
    private var ducked = false
    private var focusDucked = false

    fun load(uri: Uri, title: String) {
        close()
        allowPlay = true
        mutable.value = State(title = title, loading = true)
        val current = MediaPlayer()
        player = current
        runCatching {
            current.setAudioAttributes(attributes)
            current.setDataSource(context, uri)
            current.setOnPreparedListener {
                if (player === it) {
                    prepared = true
                    mutable.value = mutable.value.copy(loading = false)
                    if (allowPlay) play()
                }
            }
            current.setOnCompletionListener {
                if (player === it) { allowPlay = false; mutable.value = mutable.value.copy(playing = false); manager.abandonAudioFocusRequest(focus) }
            }
            current.setOnErrorListener { failed, _, _ ->
                if (player === failed) { close(); mutable.value = mutable.value.copy(error = "No pude reproducir este audio.") }
                true
            }
            current.prepareAsync()
        }.onFailure { close(); mutable.value = mutable.value.copy(error = "No pude abrir este audio.") }
    }

    fun play() {
        allowPlay = true
        if (!prepared) return
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            allowPlay = false
            mutable.value = mutable.value.copy(error = "El audio está ocupado. Intentá reproducir nuevamente.")
            return
        }
        runCatching {
            player?.start()
            focusDucked = false
            updateVolume()
            mutable.value = mutable.value.copy(playing = true, error = "")
        }.onFailure { pause(); mutable.value = mutable.value.copy(error = "No pude iniciar el audio.") }
    }

    fun pause() {
        allowPlay = false
        if (prepared) runCatching { player?.pause() }
        manager.abandonAudioFocusRequest(focus)
        mutable.value = mutable.value.copy(playing = false)
    }

    fun progress(): Float = runCatching {
        if (!prepared) 0f else (player?.currentPosition ?: 0).toFloat() / (player?.duration ?: 1).coerceAtLeast(1)
    }.getOrDefault(0f).coerceIn(0f, 1f)

    fun seek(fraction: Float) { if (prepared) runCatching { player?.seekTo(((player?.duration ?: 0) * fraction.coerceIn(0f, 1f)).toInt()) } }
    fun duck(value: Boolean) { ducked = value; updateVolume() }
    private fun updateVolume() { setVolume(if (focusDucked) 0.2f else if (ducked) 0.25f else 1f) }
    private fun setVolume(value: Float) { runCatching { player?.setVolume(value, value) } }
    fun close() {
        allowPlay = false; prepared = false; focusDucked = false
        player?.release(); player = null
        manager.abandonAudioFocusRequest(focus)
        mutable.value = mutable.value.copy(playing = false, loading = false)
    }
}
