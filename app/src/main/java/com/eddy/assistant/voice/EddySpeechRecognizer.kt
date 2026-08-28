package com.eddy.assistant.voice

import android.content.Context

/**
 * Puente de compatibilidad conservado para no romper la arquitectura del servicio.
 *
 * EDDY 0.5 es LOCAL-ONLY para voz: esta clase NO crea SpeechRecognizer, NO abre
 * el micrófono y NO envía audio al motor de reconocimiento del sistema. El audio
 * real se procesa exclusivamente mediante EddyLocalVoiceEngine + sherpa-onnx.
 *
 * Mientras los modelos locales se descargan, EDDY permanece en espera en vez de
 * degradar a un reconocedor que potencialmente pudiera usar red.
 */
class EddySpeechRecognizer(
    @Suppress("UNUSED_PARAMETER") context: Context,
    @Suppress("UNUSED_PARAMETER") private val onListeningChanged: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") private val onPartialResult: (String) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") private val onResult: (String) -> Unit,
    @Suppress("UNUSED_PARAMETER") private val onError: (String) -> Unit,
) {
    fun startContinuous() = onListeningChanged(false)

    fun pause() = onListeningChanged(false)

    fun resume() = onListeningChanged(false)

    @Suppress("UNUSED_PARAMETER")
    fun restart(delayMs: Long = 420L) = onListeningChanged(false)

    fun stopContinuous() = onListeningChanged(false)

    fun destroy() = onListeningChanged(false)
}
