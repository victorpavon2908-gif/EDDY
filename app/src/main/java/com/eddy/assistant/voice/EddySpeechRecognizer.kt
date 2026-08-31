package com.eddy.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Reconocedor de compatibilidad orientado a fiabilidad.
 *
 * EDDY permanece pasivo hasta detectar su nombre. En muchos motores Android el sonido
 * "EDDY" llega escrito como Edi/Edy/Eddie/Eddi; esas variantes se normalizan internamente
 * a EDDY, sin convertir otras conversaciones en comandos.
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
    private var wakeDispatchedThisSession = false
    private var speechStartedThisSession = false

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
        scheduleStart(100L)
    }

    fun restart(delayMs: Long = 350L) {
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
            startListeningInternal(generation)
        }, delayMs.coerceAtLeast(0L))
    }

    private fun startListeningInternal(generation: Int) {
        if (destroyed || paused || !continuous || listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            setListening(false)
            onError("No hay reconocimiento de voz disponible en este teléfono.")
            scheduleRetry(2_000L)
            return
        }

        val engine = ensureRecognizer() ?: run {
            setListening(false)
            onError("No pude iniciar el reconocimiento de voz del teléfono.")
            scheduleRetry(2_000L)
            return
        }

        wakeDispatchedThisSession = false
        speechStartedThisSession = false

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 950L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 180L)
        }

        runCatching {
            engine.startListening(intent)
            setListening(true)

            // Watchdog: algunos fabricantes dejan SpeechRecognizer abierto pero sin entregar
            // callbacks. Si ocurre, lo recreamos automáticamente en lugar de quedar "sordo".
            mainHandler.postDelayed({
                if (
                    generation == restartGeneration &&
                    continuous && !paused && !destroyed && listening && !speechStartedThisSession
                ) {
                    recreateRecognizer()
                    restart(120L)
                }
            }, 6_000L)
        }.onFailure {
            setListening(false)
            onError("No pude abrir el micrófono para escucharte.")
            recreateRecognizer()
            scheduleRetry(900L)
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        return runCatching {
            SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(this)
                recognizer = it
            }
        }.getOrNull()
    }

    private fun recreateRecognizer() {
        mainHandler.post {
            runCatching { recognizer?.destroy() }
            recognizer = null
            listening = false
            speechStartedThisSession = false
            wakeDispatchedThisSession = false
        }
    }

    private fun stopListeningInternal(cancel: Boolean) {
        mainHandler.post {
            recognizer?.let { current ->
                runCatching { if (cancel) current.cancel() else current.stopListening() }
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

    private fun normalizeWakeTranscript(value: String): String {
        val wakeAlias = Regex("(?i)(?<![\\p{L}\\p{N}])(?:eddy|edi|edy|eddie|eddi)(?![\\p{L}\\p{N}])")
        return value.replace(wakeAlias, "EDDY").trim()
    }

    private fun bestText(bundle: Bundle?): String? {
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            .orEmpty()
        if (results.isEmpty()) return null

        val wakeAlias = Regex("(?i)(?<![\\p{L}\\p{N}])(?:eddy|edi|edy|eddie|eddi)(?![\\p{L}\\p{N}])")
        val selected = results.firstOrNull { wakeAlias.containsMatchIn(it) } ?: results.first()
        return normalizeWakeTranscript(selected)
    }

    private fun containsWakeWord(text: String): Boolean =
        Regex("(?i)(?<![\\p{L}\\p{N}])EDDY(?![\\p{L}\\p{N}])").containsMatchIn(text)

    override fun onReadyForSpeech(params: Bundle?) = setListening(true)

    override fun onBeginningOfSpeech() {
        speechStartedThisSession = true
        setListening(true)
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = setListening(false)

    override fun onError(error: Int) {
        setListening(false)
        if (destroyed || paused || !continuous) return

        val message = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono para que EDDY pueda escucharte."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono estaba ocupado. Recuperando escucha."
            SpeechRecognizer.ERROR_AUDIO -> "Hubo un problema con el micrófono. Recuperando escucha."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El reconocimiento de voz no respondió. EDDY seguirá intentando."
            SpeechRecognizer.ERROR_CLIENT -> "El reconocimiento de voz se reinició."
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "El reconocimiento del sistema se desconectó. Reiniciando."
            else -> "No te pude escuchar bien. Intentando de nuevo."
        }

        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            onError(message)
        }

        if (
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_AUDIO
        ) {
            recreateRecognizer()
        }
        val retry = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 4_000L
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 700L
            else -> 350L
        }
        scheduleRetry(retry)
    }

    override fun onResults(results: Bundle?) {
        setListening(false)
        bestText(results)?.let { text ->
            if (!wakeDispatchedThisSession || !containsWakeWord(text)) onResult(text)
        }
        scheduleRetry(180L)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = bestText(partialResults) ?: return
        onPartialResult(text)

        // Si el motor ya oyó EDDY, no esperamos al resultado final: activamos en ese mismo
        // instante. Esto evita la sensación de que EDDY ignora el llamado en equipos lentos.
        if (!wakeDispatchedThisSession && containsWakeWord(text)) {
            wakeDispatchedThisSession = true
            onResult(text)
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
