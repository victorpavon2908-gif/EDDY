package com.niko.assistant.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Capa de compatibilidad para teléfonos donde el runtime nativo KWS/ONNX no puede abrirse
 * de forma estable. Usa el reconocedor de voz provisto por Android y pide modo offline
 * cuando el proveedor lo soporta.
 *
 * No reemplaza el motor nativo para siempre: es una ruta segura para que LEO pueda escuchar
 * y ejecutar órdenes sin dejar la aplicación inutilizable por un fallo JNI.
 */
class LeoPlatformVoiceEngine(
    context: Context,
    private val onState: (State) -> Unit = {},
    private val onWake: () -> Unit,
    private val onCommand: (String) -> Unit,
    private val onStatus: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
) : RecognitionListener {
    enum class State { PASSIVE, ACTIVE, PROCESSING, SPEAKING, STOPPED }

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var speaking = false
    private var awaitingCommand = false
    private var continueAfterSpeech = false
    private var restartAttempt = 0
    private var destroyed = false

    val isRunning: Boolean get() = running && !destroyed
    val isMicrophoneSilenced: Boolean
        get() = appContext.getSystemService(AudioManager::class.java)?.isMicrophoneMute == true

    fun start(): Boolean {
        if (destroyed) return false
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("Falta el permiso de micrófono.")
            return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Android no tiene un servicio de reconocimiento de voz disponible.")
            return false
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            onError("El motor compatible debe iniciarse desde el hilo principal.")
            return false
        }
        return runCatching {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also {
                it.setRecognitionListener(this)
            }
            running = true
            restartAttempt = 0
            awaitingCommand = false
            onState(State.PASSIVE)
            onStatus("Micrófono listo · modo compatible · decí LEO")
            startListening(120L)
            true
        }.getOrElse {
            recognizer = null
            running = false
            onError("No pude iniciar el reconocimiento de voz de Android.")
            false
        }
    }

    fun stop() {
        destroyed = true
        running = false
        awaitingCommand = false
        main.removeCallbacksAndMessages(null)
        main.post {
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            onState(State.STOPPED)
        }
    }

    fun stopAndAwait(@Suppress("UNUSED_PARAMETER") timeoutMillis: Long = 5_000L): Boolean {
        stop()
        return true
    }

    fun setAssistantBusy(value: Boolean) {
        if (value) pauseForAssistant()
        else if (!speaking && running) startListening(160L)
    }

    fun cancelConversation() {
        awaitingCommand = false
        continueAfterSpeech = false
        if (!speaking && running) {
            onState(State.PASSIVE)
            startListening(120L)
        }
    }

    fun setAssistantSpeaking(value: Boolean, continueCommand: Boolean = false) {
        speaking = value
        continueAfterSpeech = continueCommand
        if (value) {
            pauseForAssistant()
            onState(State.SPEAKING)
        } else if (running) {
            awaitingCommand = continueAfterSpeech
            onState(if (awaitingCommand) State.ACTIVE else State.PASSIVE)
            startListening(220L)
        }
    }

    fun finishTurn() {
        awaitingCommand = false
        continueAfterSpeech = false
        if (!speaking && running) {
            onState(State.PASSIVE)
            startListening(160L)
        }
    }

    private fun pauseForAssistant() {
        main.post {
            runCatching { recognizer?.cancel() }
        }
    }

    private fun startListening(delayMs: Long) {
        if (!running || destroyed || speaking) return
        main.removeCallbacks(startRunnable)
        main.postDelayed(startRunnable, delayMs)
    }

    private val startRunnable = Runnable {
        if (!running || destroyed || speaking) return@Runnable
        val sr = recognizer ?: return@Runnable
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, preferredLanguage())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, preferredLanguage())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (awaitingCommand) 900L else 650L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 550L)
        }
        runCatching { sr.startListening(intent) }
            .onFailure {
                onError("El servicio de voz de Android no pudo abrir el micrófono.")
                scheduleRecovery()
            }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        restartAttempt = 0
        onStatus(if (awaitingCommand) "Te escucho…" else "Micrófono listo · decí LEO")
        onState(if (awaitingCommand) State.ACTIVE else State.PASSIVE)
    }

    override fun onBeginningOfSpeech() {
        if (awaitingCommand) onState(State.ACTIVE)
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        if (awaitingCommand) onState(State.PROCESSING)
    }

    override fun onError(error: Int) {
        if (!running || destroyed || speaking) return
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                onError("Android bloqueó el permiso del micrófono.")
            SpeechRecognizer.ERROR_AUDIO ->
                onError("Android no pudo capturar audio del micrófono.")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                onStatus("Reiniciando micrófono…")
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                onStatus("Reconocimiento sin conexión no disponible · reintentando")
            else -> Unit
        }
        scheduleRecovery()
    }

    override fun onResults(results: Bundle?) {
        if (!running || destroyed) return
        val phrases = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)
        if (phrases.isEmpty()) {
            scheduleRecovery()
            return
        }

        if (awaitingCommand) {
            val command = phrases.firstOrNull().orEmpty().trim()
            awaitingCommand = false
            if (command.isNotBlank()) {
                onState(State.PROCESSING)
                onCommand(command)
            } else {
                onState(State.PASSIVE)
                startListening(120L)
            }
            return
        }

        val wake = phrases.asSequence().mapNotNull(::extractWakeCommand).firstOrNull()
        if (wake == null) {
            onState(State.PASSIVE)
            startListening(90L)
            return
        }

        onWake()
        val inlineCommand = wake.trim()
        if (inlineCommand.isNotBlank()) {
            onState(State.PROCESSING)
            onCommand(inlineCommand)
        } else {
            awaitingCommand = true
            onState(State.ACTIVE)
            onStatus("Te escucho…")
            startListening(130L)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun scheduleRecovery() {
        if (!running || destroyed || speaking) return
        val delayMs = when (restartAttempt.coerceAtMost(5)) {
            0 -> 180L
            1 -> 300L
            2 -> 500L
            3 -> 800L
            else -> 1_200L
        }
        restartAttempt++
        startListening(delayMs)
    }

    private fun preferredLanguage(): String {
        val locale = Locale.getDefault()
        return if (locale.language.equals("es", ignoreCase = true)) locale.toLanguageTag() else "es-NI"
    }

    companion object {
        private val WAKE = Regex("(?i)(?:^|\\s|[¿¡,.;:!?])leo(?:\\s|[¿¡,.;:!?]|$)")

        internal fun extractWakeCommand(value: String): String? {
            val match = WAKE.find(value.trim()) ?: return null
            return value.substring(match.range.last + 1)
                .trim()
                .trimStart(',', '.', ';', ':', '¿', '?', '¡', '!')
                .trim()
        }
    }
}
