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
import kotlin.math.min

/** Compatibility path only: Android recognition sessions are not an always-on wake engine. */
class EddySpeechRecognizer(
    context: Context,
    private val onListeningChanged: (Boolean) -> Unit,
    private val onPartialResult: (String) -> Unit = {},
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var enabled = false
    private var destroyed = false
    private var sessionActive = false
    private var listening = false
    private var scheduled = false
    private var engineEpoch = 0
    private var errors = 0
    private var useSystemEngine = false
    private var onDevice = false
    private val startTask = Runnable { scheduled = false; startSession() }
    private val watchdog = Runnable {
        if (enabled && sessionActive) {
            if (onDevice) useSystemEngine = true
            releaseRecognizer()
            onError("El reconocimiento dejó de responder. Recuperando escucha.")
            schedule(1_000L)
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post { action() }
    }

    fun startContinuous() = resume()
    fun resume() = onMain {
        if (!destroyed) { enabled = true; schedule(100L) }
    }
    fun pause() = onMain {
        enabled = false
        cancelScheduled()
        releaseRecognizer()
    }
    fun stopContinuous() = pause()
    fun restart(delayMs: Long = 350L) = onMain {
        if (!destroyed) {
            enabled = true
            cancelScheduled()
            releaseRecognizer()
            schedule(delayMs)
        }
    }
    fun destroy() = onMain {
        destroyed = true
        enabled = false
        cancelScheduled()
        releaseRecognizer()
    }

    private fun cancelScheduled() {
        handler.removeCallbacks(startTask)
        handler.removeCallbacks(watchdog)
        scheduled = false
    }

    private fun schedule(delayMs: Long) {
        if (!enabled || destroyed || sessionActive || scheduled) return
        scheduled = true
        handler.postDelayed(startTask, delayMs)
    }

    private fun startSession() {
        if (!enabled || destroyed || sessionActive) return
        val engine = ensureRecognizer() ?: run {
            onError("No hay reconocimiento compatible disponible. Prepará la voz local de EDDY.")
            schedule(5_000L)
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_300L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_000L)
        }
        try {
            sessionActive = true
            engine.startListening(intent)
            // Recover only a genuinely stuck provider, never periodically cancel healthy listening.
            handler.postDelayed(watchdog, 60_000L)
        } catch (_: RuntimeException) {
            errors++
            releaseRecognizer()
            onError("No pude abrir el micrófono. Recuperando escucha.")
            schedule(backoff())
        }
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        return runCatching {
            onDevice = !useSystemEngine && Build.VERSION.SDK_INT >= 31 &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            val engine = if (onDevice && Build.VERSION.SDK_INT >= 31) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                check(SpeechRecognizer.isRecognitionAvailable(appContext))
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            val epoch = ++engineEpoch
            fun current() = epoch == engineEpoch && enabled && !destroyed && sessionActive
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (current()) setListening(true) }
                override fun onBeginningOfSpeech() { if (current()) { errors = 0; setListening(true) } }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                // End-of-speech is NOT terminal. Wait for onResults/onError before another start.
                override fun onEndOfSpeech() { if (current()) setListening(false) }
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    endSession()
                    errors = 0
                    bestText(results)?.let(onResult)
                    schedule(250L)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (current()) bestText(partialResults)?.let(onPartialResult)
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    endSession()
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        enabled = false
                        releaseRecognizer()
                        onError("Falta el permiso de micrófono. Activá el permiso en Ajustes.")
                        return
                    }
                    val quiet = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (!quiet) errors++
                    if (onDevice && error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)) {
                        useSystemEngine = true
                        releaseRecognizer()
                    } else if (!quiet && errors >= 2) {
                        // A present on-device provider can still be broken or lack its model.
                        // Do not loop forever on that same provider.
                        if (onDevice) useSystemEngine = true
                        releaseRecognizer()
                    }
                    if (!quiet) onError("El reconocimiento compatible no respondió. Recuperando escucha.")
                    schedule(if (quiet) 350L else backoff())
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer = engine
            engine
        }.getOrNull()
    }

    private fun endSession() {
        sessionActive = false
        handler.removeCallbacks(watchdog)
        setListening(false)
    }

    private fun releaseRecognizer() {
        ++engineEpoch // Ignore callbacks from cancel/destroy and previous engines.
        val old = recognizer
        recognizer = null
        endSession()
        runCatching { old?.cancel() }
        runCatching { old?.destroy() }
    }

    private fun backoff() = min(500L * (1L shl errors.coerceIn(0, 4)), 8_000L)
    private fun setListening(value: Boolean) {
        if (listening != value) { listening = value; onListeningChanged(value) }
    }

    private fun bestText(bundle: Bundle?): String? = bundle
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()?.trim()?.takeIf(String::isNotBlank)
}
