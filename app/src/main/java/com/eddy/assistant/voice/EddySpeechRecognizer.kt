package com.eddy.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class EddySpeechRecognizer(
    private val context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialResult: (String) -> Unit = {},
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var continuousMode = false
    private var paused = true
    private var listening = false
    private var destroyed = false

    private var useOnDeviceRecognizer =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private var preferOffline = useOnDeviceRecognizer
    private var languageIndex = 0
    private var useDeviceDefaultLanguage = false

    private val languageCandidates: List<String> by lazy {
        buildList {
            val defaultTag = Locale.getDefault().toLanguageTag()
            if (defaultTag.startsWith("es", ignoreCase = true)) {
                add(defaultTag)
            }
            add("es-US")
            add("es-ES")
            add("es-MX")
        }.distinct()
    }

    private val restartRunnable = Runnable { startSession() }

    fun startContinuous() {
        continuousMode = true
        paused = false
        scheduleRestart(0)
    }

    fun pause() {
        paused = true
        handler.removeCallbacks(restartRunnable)
        if (listening) {
            recognizer?.cancel()
        }
        setListening(false)
    }

    fun resume() {
        if (destroyed) return
        continuousMode = true
        paused = false
        scheduleRestart(280)
    }

    /**
     * Rearma por completo una sesión de escucha. Se usa al apagarse/encenderse la
     * pantalla porque varios proveedores OEM suspenden la sesión activa aunque el
     * foreground service siga vivo.
     */
    fun restart(delayMs: Long = 420L) {
        if (destroyed) return
        continuousMode = true
        paused = false
        handler.removeCallbacks(restartRunnable)
        runCatching { recognizer?.cancel() }
        setListening(false)
        scheduleRestart(delayMs)
    }

    fun stopContinuous() {
        continuousMode = false
        paused = true
        handler.removeCallbacks(restartRunnable)
        recognizer?.cancel()
        setListening(false)
    }

    fun destroy() {
        destroyed = true
        continuousMode = false
        paused = true
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        setListening(false)
    }

    private fun startSession() {
        if (destroyed || paused || !continuousMode || listening) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("El reconocimiento de voz no está disponible en este dispositivo.")
            scheduleRestart(2_000)
            return
        }

        if (recognizer == null) {
            recognizer = createRecognizer().apply {
                setRecognitionListener(listener)
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (!useDeviceDefaultLanguage) {
                languageCandidates.getOrNull(languageIndex)?.let { languageTag ->
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                }
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (preferOffline) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 750L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
        }

        runCatching {
            setListening(true)
            recognizer?.startListening(intent)
        }.onFailure {
            setListening(false)
            scheduleRestart(650)
        }
    }

    private fun createRecognizer(): SpeechRecognizer {
        if (useOnDeviceRecognizer && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun recreateRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
        setListening(false)
    }

    /**
     * ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE no deben romper EDDY.
     * Algunos Samsung anuncian reconocimiento local disponible aunque no tengan el
     * modelo es-NI descargado. Primero salimos del motor on-device, luego probamos
     * variantes de español y por último dejamos que Android use el idioma por defecto.
     */
    private fun recoverFromLanguageError(): Boolean {
        if (useOnDeviceRecognizer) {
            useOnDeviceRecognizer = false
            preferOffline = false
            recreateRecognizer()
            scheduleRestart(350)
            return true
        }

        if (!useDeviceDefaultLanguage && languageIndex < languageCandidates.lastIndex) {
            languageIndex += 1
            recreateRecognizer()
            scheduleRestart(350)
            return true
        }

        if (!useDeviceDefaultLanguage) {
            useDeviceDefaultLanguage = true
            recreateRecognizer()
            scheduleRestart(350)
            return true
        }

        return false
    }

    private fun scheduleRestart(delayMs: Long) {
        if (destroyed || paused || !continuousMode) return
        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(restartRunnable, delayMs)
    }

    private fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        onListeningChanged(value)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (partial.isNotBlank()) onPartialResult(partial)
        }

        override fun onResults(results: Bundle?) {
            setListening(false)
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val best = matches?.firstOrNull()
            if (best.isNullOrBlank()) {
                scheduleRestart(280)
            } else {
                paused = true
                onResult(best)
            }
        }

        override fun onError(error: Int) {
            setListening(false)

            if (
                error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
            ) {
                if (recoverFromLanguageError()) return

                continuousMode = false
                paused = true
                onError(
                    "El motor de voz del teléfono no tiene un modelo de español disponible. " +
                        "Activa o descarga Español en los ajustes de reconocimiento de voz y vuelve a abrir EDDY.",
                )
                return
            }

            val recoverable = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT,
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> true
                else -> false
            }

            if (recoverable) {
                scheduleRestart(
                    when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 800
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 1_200
                        else -> 300
                    },
                )
                return
            }

            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Hubo un problema con el audio del micrófono."
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Necesito permiso para usar el micrófono."
                else -> "No pude reconocer la voz. Código: $error"
            }

            onError(message)
            scheduleRestart(1_500)
        }
    }
}
