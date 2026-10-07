package com.niko.assistant.voice

import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Canal local, dentro del proceso, para una interacción de voz realmente dúplex.
 *
 * - El motor de escucha publica una transcripción provisional mientras el usuario habla.
 * - Si se detecta "Leo" durante el TTS, solicita que todos los reproductores se callen.
 *
 * No envía audio ni texto fuera del teléfono.
 */
object LeoRealtimeTurnBus {
    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()
    private var transcriptGeneration = 0L

    private var speechOwner: String? = null
    private var speechReference = ""
    private var speechEndedAt = 0L

    @Synchronized fun beginSpeechReference(owner: String, text: String) {
        speechOwner = owner
        speechReference = text
        speechEndedAt = 0L
    }

    @Synchronized fun endSpeechReference(owner: String?) {
        if (owner != null && owner == speechOwner) {
            speechOwner = null
            speechEndedAt = System.nanoTime()
        }
    }

    /** Keep a short tail for recognizer results arriving after playback ends. */
    @Synchronized fun spokenReference(): String =
        if (speechOwner != null || (speechEndedAt != 0L && System.nanoTime() - speechEndedAt < 800_000_000L)) speechReference else ""

    private val speechStoppers = CopyOnWriteArraySet<() -> Unit>()
    private val turnInterrupters = CopyOnWriteArraySet<() -> Unit>()

    fun registerTurnInterrupter(interrupter: () -> Unit) { turnInterrupters += interrupter }
    fun unregisterTurnInterrupter(interrupter: () -> Unit) { turnInterrupters -= interrupter }

    /** Cancel the producer as well as playback, so an old search cannot speak later. */
    fun interruptTurn() {
        turnInterrupters.forEach { runCatching(it) }
        interruptSpeech()
    }

    @Synchronized fun updateTranscript(text: String) {
        transcriptGeneration++
        _liveTranscript.value = text.trim()
    }

    @Synchronized fun previewToken(): Long = transcriptGeneration

    @Synchronized fun updatePreview(text: String, token: Long) {
        if (token == transcriptGeneration) _liveTranscript.value = text.trim()
    }

    @Synchronized fun clearTranscript() {
        transcriptGeneration++
        _liveTranscript.value = ""
    }

    fun registerSpeechStopper(stopper: () -> Unit) {
        speechStoppers += stopper
    }

    fun unregisterSpeechStopper(stopper: () -> Unit) {
        speechStoppers -= stopper
    }

    fun interruptSpeech() {
        speechStoppers.forEach { stopper -> runCatching(stopper) }
    }
}
