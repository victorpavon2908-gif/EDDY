package com.eddy.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class EddySpeechRecognizer(
    private val context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("El reconocimiento de voz no está disponible en este dispositivo.")
            return
        }

        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-NI")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-NI")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        onListeningChanged(true)
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        onListeningChanged(false)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = onListeningChanged(false)
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            onListeningChanged(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()
            if (best.isNullOrBlank()) {
                onError("No entendí eso. Intenta de nuevo.")
            } else {
                onResult(best)
            }
        }

        override fun onError(error: Int) {
            onListeningChanged(false)
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Hubo un problema con el audio."
                SpeechRecognizer.ERROR_CLIENT -> "Se interrumpió el reconocimiento."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Necesito permiso para usar el micrófono."
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No pude usar el reconocimiento de voz por un problema de red."
                SpeechRecognizer.ERROR_NO_MATCH -> "No entendí lo que dijiste."
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocimiento de voz está ocupado."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No escuché ninguna voz."
                else -> "No pude reconocer la voz. Código: $error"
            }
            onError(message)
        }
    }
}
