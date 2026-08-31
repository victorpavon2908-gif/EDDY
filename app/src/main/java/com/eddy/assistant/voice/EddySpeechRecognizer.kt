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

/**
 * Fallback de voz para cuando el núcleo local todavía no está listo o no pudo iniciar.
 *
 * En Android 12+ prioriza el reconocedor on-device cuando está disponible. En equipos
 * anteriores usa el reconocedor del sistema con EXTRA_PREFER_OFFLINE=true. El motor local
 * de EDDY sigue siendo la ruta principal; este fallback existe para que el asistente no
 * quede sordo durante descargas, reparaciones o fallos de inicialización.
 */
class EddySpeechRecognizer(
    context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialResult: (String) -> Unit = {},
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) : RecognitionListener {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var continuous = false
    private var paused = false
    private var destroyed = false
    private var listening = false
    private var restartGeneration = 0

    fun startContinuous() {
        continuous = true
        paused = false
        scheduleStart(0L)
    }

    fun pause() {
        paused = true
        restartGeneration++
        stopListeningInternal(cancel = true)
    }

    fun resume() {
        if (destroyed) return
        continuous = true
        paused = false
        scheduleStart(120L)
    }

    fun restart(delayMs: Long = 420L) {
        if (destroyed) return
        continuous = true
        paused = false
        restartGeneration++
        stopListeningInternal(cancel = true)
        scheduleStart(delayMs)
    }

    fun stopContinuous() {
        continuous = false
        paused = true
        restartGeneration++
        stopListeningInternal(cancel = true)
    }

    fun destroy() {
        destroyed = true
        continuous = false
        paused = true
        restartGeneration++
        mainHandler.post {
            stopListeningInternal(cancel = true)
            runCatching { recognizer?.destroy() }
            recognizer = null
            setListening(false)
        }
    }

    private fun scheduleStart(delayMs: Long) {
        val generation = ++restartGeneration
        mainHandler.postDelayed({
            if (generation != restartGeneration || destroyed || paused || !continuous) return@postDelayed
            startListeningInternal()
        }, delayMs.coerceAtLeast(0L))
    }

    private fun startListeningInternal() {
        if (destroyed || paused || !continuous || listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            setListening(false)
            onError("No hay reconocimiento de voz disponible en este teléfono.")
            scheduleRetry(2_500L)
            return
        }

        val engine = ensureRecognizer() ?: run {
            setListening(false)
            onError("No pude iniciar el reconocimiento de voz del teléfono.")
            scheduleRetry(2_500L)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-NI")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-NI")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 250L)
        }

        runCatching {
            engine.startListening(intent)
            setListening(true)
        }.onFailure {
            setListening(false)
            onError("No pude abrir el micrófono para escucharte.")
            recreateRecognizer()
            scheduleRetry(1_500L)
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        return runCatching {
            val created = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            created.setRecognitionListener(this)
            recognizer = created
            created
        }.getOrNull()
    }

    private fun recreateRecognizer() {
        mainHandler.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
            listening = false
        }
    }

    private fun stopListeningInternal(cancel: Boolean) {
        mainHandler.post {
            val current = recognizer
            if (current != null) {
                runCatching {
                    if (cancel) current.cancel() else current.stopListening()
                }
            }
            setListening(false)
        }
    }

    private fun scheduleRetry(delayMs: Long) {
        if (!continuous || paused || destroyed) return
        scheduleStart(delayMs)
    }

    private fun setListening(value: Boolean) {
        if (listening == value) return
        listening = value
        onListeningChanged(value)
    }

    private fun bestText(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    override fun onReadyForSpeech(params: Bundle?) = setListening(true)
    override fun onBeginningOfSpeech() = setListening(true)
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = setListening(false)

    override fun onError(error: Int) {
        setListening(false)
        if (destroyed || paused || !continuous) return

        val message = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono para que EDDY pueda escucharte."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono estaba ocupado. Voy a intentar escucharte de nuevo."
            SpeechRecognizer.ERROR_AUDIO -> "Hubo un problema con el micrófono. Voy a intentar recuperarlo."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El reconocedor del sistema no respondió. EDDY seguirá intentando en modo local."
            SpeechRecognizer.ERROR_CLIENT -> "El reconocimiento de voz se reinició."
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "El reconocimiento del sistema se desconectó. Voy a reiniciarlo."
            else -> "No te pude escuchar bien. Intentando de nuevo."
        }

        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            onError(message)
        }

        if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            recreateRecognizer()
        }
        val retry = if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) 4_000L else 550L
        scheduleRetry(retry)
    }

    override fun onResults(results: Bundle?) {
        setListening(false)
        bestText(results)?.let(onResult)
        scheduleRetry(280L)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        bestText(partialResults)?.let(onPartialResult)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
