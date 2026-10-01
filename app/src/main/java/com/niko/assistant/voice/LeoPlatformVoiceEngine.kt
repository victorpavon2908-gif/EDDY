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
    private val onFatal: (String) -> Unit = {},
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
    private var listenGeneration = 0
    private var readyGeneration = 0
    private var wakeNotifiedGeneration = -1
    private var consecutiveReadyTimeouts = 0
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
            // No forzamos EXTRA_PREFER_OFFLINE: varios proveedores OEM (incluido HONOR)
            // aceptan la sesión pero no devuelven resultados fiables si no tienen un
            // paquete offline instalado. Dejamos que Android elija su mejor backend.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (awaitingCommand) 900L else 650L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 550L)
        }
        val generation = ++listenGeneration
        runCatching { sr.startListening(intent) }
            .onSuccess {
                main.postDelayed({
                    if (!running || destroyed || speaking || readyGeneration >= generation) return@postDelayed
                    runCatching { sr.cancel() }
                    consecutiveReadyTimeouts++
                    if (consecutiveReadyTimeouts >= MAX_READY_TIMEOUTS) {
                        running = false
                        onFatal("Android no confirmó la apertura del micrófono.")
                    } else {
                        onStatus("El micrófono tardó en responder · reintentando…")
                        scheduleRecovery()
                    }
                }, READY_TIMEOUT_MS)
            }
            .onFailure {
                onError("El servicio de voz de Android no pudo abrir el micrófono.")
                scheduleRecovery()
            }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        readyGeneration = listenGeneration
        consecutiveReadyTimeouts = 0
        restartAttempt = 0
        onStatus(if (awaitingCommand) "Te escucho…" else "Micrófono listo · decí LEO")
        onState(if (awaitingCommand) State.ACTIVE else State.PASSIVE)
    }

    override fun onBeginningOfSpeech() {
        if (awaitingCommand) onState(State.ACTIVE)
        else onStatus("Oyéndote…")
    }

    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() {
        if (awaitingCommand) onState(State.PROCESSING)
    }

    override fun onError(error: Int) {
        if (!running || destroyed || speaking) return

        if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            running = false
            onFatal("Android bloqueó el permiso del micrófono.")
            return
        }

        // En varios RecognitionService OEM una palabra muy corta como "LEO" llega como
        // resultado parcial y luego la sesión termina en NO_MATCH/SPEECH_TIMEOUT. Si ya
        // detectamos LEO en ese parcial, el wake es válido y abrimos una nueva sesión
        // para escuchar la orden en vez de descartarlo.
        if (!awaitingCommand &&
            wakeNotifiedGeneration == listenGeneration &&
            (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        ) {
            awaitingCommand = true
            onState(State.ACTIVE)
            onStatus("Te escucho…")
            startListening(140L)
            return
        }

        when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                onError("Android no pudo capturar audio del micrófono.")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                onStatus("Reiniciando micrófono…")
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                onStatus("El reconocimiento necesita conexión · reintentando")
            else -> Unit
        }
        scheduleRecovery()
    }

    override fun onResults(results: Bundle?) {
        if (!running || destroyed) return
        val phrases = recognitionPhrases(results)

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
        val wakeWasAlreadyHeard = wakeNotifiedGeneration == listenGeneration
        if (wake == null && !wakeWasAlreadyHeard) {
            onState(State.PASSIVE)
            startListening(90L)
            return
        }

        if (!wakeWasAlreadyHeard) notifyWakeForCurrentSession()
        val inlineCommand = wake.orEmpty().trim()
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

    override fun onPartialResults(partialResults: Bundle?) {
        if (!running || destroyed || speaking || awaitingCommand) return
        val wake = recognitionPhrases(partialResults)
            .asSequence()
            .mapNotNull(::extractWakeCommand)
            .firstOrNull()
            ?: return

        // El parcial es suficiente para despertar la interfaz inmediatamente. Esperamos
        // el resultado final para decidir si venía una orden en la misma frase.
        notifyWakeForCurrentSession()
        onState(State.ACTIVE)
        onStatus(if (wake.isBlank()) "Te escucho…" else "LEO detectado…")
    }
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun notifyWakeForCurrentSession() {
        if (wakeNotifiedGeneration == listenGeneration) return
        wakeNotifiedGeneration = listenGeneration
        onWake()
    }

    private fun recognitionPhrases(bundle: Bundle?): List<String> =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            .map(String::trim)
            .filter(String::isNotBlank)

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
        private const val READY_TIMEOUT_MS = 4_000L
        private const val MAX_READY_TIMEOUTS = 3
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
