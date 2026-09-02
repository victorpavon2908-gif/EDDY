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

    private val speechStoppers = CopyOnWriteArraySet<() -> Unit>()

    fun updateTranscript(text: String) {
        _liveTranscript.value = text.trim().take(320)
    }

    fun clearTranscript() {
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
