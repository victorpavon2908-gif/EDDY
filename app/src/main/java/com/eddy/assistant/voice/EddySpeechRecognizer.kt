package com.eddy.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.eddy.assistant.ai.EddyBackendPrewarmer
import kotlin.math.min

/** Reconocedor compatible estable y de baja latencia para la ruta de respaldo. */
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
    private var speechStartedThisSession = false
    private var backendWarmRequested = false
    private var consecutiveErrors = 0

    fun startContinuous() { continuous = true; paused = false; scheduleStart(0L) }
    fun pause() { paused = true; restartGeneration++; stopListeningInternal(cancel = true) }
    fun resume() { if (!destroyed) { continuous = true; paused = false; scheduleStart(100L) } }
    fun restart(delayMs: Long = 350L) {
        if (destroyed) return
        continuous = true; paused = false; restartGeneration++
        stopListeningInternal(cancel = true); scheduleStart(delayMs)
    }
    fun stopContinuous() { continuous = false; paused = true; restartGeneration++; stopListeningInternal(cancel = true) }
    fun destroy() {
        destroyed = true; continuous = false; paused = true; restartGeneration++
        mainHandler.post { stopListeningInternal(true); runCatching { recognizer?.destroy() }; recognizer = null; setListening(false) }
    }

    private fun scheduleStart(delayMs: Long) {
        val generation = ++restartGeneration
        mainHandler.postDelayed({ if (generation == restartGeneration && !destroyed && !paused && continuous) startListeningInternal(generation) }, delayMs.coerceAtLeast(0L))
    }

    private fun startListeningInternal(generation: Int) {
        if (destroyed || paused || !continuous || listening) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            setListening(false); onError("No hay reconocimiento de voz disponible en este teléfono."); scheduleRetry(3_000L); return
        }
        val engine = ensureRecognizer() ?: run { setListening(false); onError("No pude iniciar el reconocimiento de voz del teléfono."); scheduleRetry(2_000L); return }
        speechStartedThisSession = false; backendWarmRequested = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 650L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 180L)
        }
        runCatching {
            engine.startListening(intent); setListening(true)
            mainHandler.postDelayed({
                if (generation == restartGeneration && continuous && !paused && !destroyed && listening && !speechStartedThisSession) {
                    // Renovamos la sesión sin destruir el motor. Evita el parpadeo/reinicio constante del micrófono.
                    restart(300L)
                }
            }, IDLE_SESSION_MS)
        }.onFailure {
            setListening(false); consecutiveErrors++
            onError("No pude abrir el micrófono para escucharte.")
            if (consecutiveErrors >= 3) recreateRecognizer()
            scheduleRetry(backoffDelay(800L))
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        return runCatching { SpeechRecognizer.createSpeechRecognizer(appContext).also { it.setRecognitionListener(this); recognizer = it } }.getOrNull()
    }

    private fun recreateRecognizer() {
        mainHandler.post { runCatching { recognizer?.destroy() }; recognizer = null; listening = false; speechStartedThisSession = false; backendWarmRequested = false }
    }

    private fun stopListeningInternal(cancel: Boolean) {
        mainHandler.post { recognizer?.let { current -> runCatching { if (cancel) current.cancel() else current.stopListening() } }; setListening(false) }
    }

    private fun scheduleRetry(delayMs: Long) { if (continuous && !paused && !destroyed) scheduleStart(delayMs) }
    private fun backoffDelay(base: Long): Long = min(base * (1L shl consecutiveErrors.coerceIn(0, 3)), 4_000L)
    private fun setListening(value: Boolean) { if (listening != value) { listening = value; onListeningChanged(value) } }

    private fun normalizeWakeTranscript(value: String): String {
        val wakeAlias = Regex("(?i)(?<![\\p{L}\\p{N}])(?:eddy|edi|edy|eddie|eddi)(?![\\p{L}\\p{N}])")
        return value.replace(wakeAlias, "EDDY").trim()
    }

    private fun bestText(bundle: Bundle?): String? {
        val results = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.map(String::trim)?.filter(String::isNotBlank).orEmpty()
        if (results.isEmpty()) return null
        val wakeAlias = Regex("(?i)(?<![\\p{L}\\p{N}])(?:eddy|edi|edy|eddie|eddi)(?![\\p{L}\\p{N}])")
        return normalizeWakeTranscript(results.firstOrNull { wakeAlias.containsMatchIn(it) } ?: results.first())
    }

    private fun containsWakeWord(text: String) = Regex("(?i)(?<![\\p{L}\\p{N}])EDDY(?![\\p{L}\\p{N}])").containsMatchIn(text)
    override fun onReadyForSpeech(params: Bundle?) { consecutiveErrors = 0; setListening(true) }
    override fun onBeginningOfSpeech() { speechStartedThisSession = true; consecutiveErrors = 0; setListening(true) }
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = setListening(false)

    override fun onError(error: Int) {
        setListening(false)
        if (destroyed || paused || !continuous) return
        val quietError = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        if (!quietError) consecutiveErrors++ else consecutiveErrors = 0
        val message = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono para que EDDY pueda escucharte."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El micrófono estaba ocupado. Recuperando escucha."
            SpeechRecognizer.ERROR_AUDIO -> "Hubo un problema con el micrófono. Recuperando escucha."
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El reconocimiento de voz no respondió. EDDY seguirá intentando."
            SpeechRecognizer.ERROR_CLIENT -> "El reconocimiento de voz se reinició."
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "El reconocimiento del sistema se desconectó. Reiniciando."
            else -> "No te pude escuchar bien. Intentando de nuevo."
        }
        if (!quietError) onError(message)
        if ((error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED || error == SpeechRecognizer.ERROR_AUDIO) && consecutiveErrors >= 2) recreateRecognizer()
        val delay = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> 4_000L
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> backoffDelay(500L)
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 350L
            else -> backoffDelay(350L)
        }
        scheduleRetry(delay)
    }

    override fun onResults(results: Bundle?) {
        setListening(false); consecutiveErrors = 0
        bestText(results)?.let(onResult)
        scheduleRetry(220L)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = bestText(partialResults) ?: return
        onPartialResult(text)
        if (!backendWarmRequested && containsWakeWord(text)) { backendWarmRequested = true; EddyBackendPrewarmer.wake(appContext) }
    }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    companion object { private const val IDLE_SESSION_MS = 14_000L }
}
