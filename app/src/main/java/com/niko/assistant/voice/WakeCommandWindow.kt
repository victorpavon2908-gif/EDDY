package com.niko.assistant.voice

/** Audio-worker state: only an acoustic wake opens a command window. */
class WakeCommandWindow(private val durationMillis: Long = 30_000L) {
    private var deadline = 0L

    fun onWake(now: Long) { deadline = now + durationMillis }

    fun isOpen(now: Long): Boolean {
        if (now >= deadline) close()
        return deadline > 0L
    }

    /** An acknowledgement/retry may continue a wake, but TTS alone cannot create one. */
    fun continueAfterPrompt(now: Long) {
        if (deadline > 0L) deadline = now + durationMillis
    }

    fun close() { deadline = 0L }
}
