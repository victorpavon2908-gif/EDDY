package com.niko.assistant.voice

/**
 * Audio-worker state. Un wake acústico abre la primera orden; después de una respuesta
 * autorizada puede abrirse una ventana de seguimiento más corta para conversar sin
 * repetir NIKO en cada frase.
 */
class WakeCommandWindow(
    private val durationMillis: Long = 30_000L,
    private val followUpMillis: Long = 12_000L,
) {
    private var deadline = 0L

    fun onWake(now: Long) {
        deadline = now + durationMillis
    }

    fun isOpen(now: Long): Boolean {
        if (now >= deadline) close()
        return deadline > 0L
    }

    /** Un aviso/reintento solo extiende una ventana que ya estaba autorizada. */
    fun continueAfterPrompt(now: Long) {
        if (deadline > 0L) deadline = now + durationMillis
    }

    /** Solo el motor de voz debe llamarlo después de un wake acústico previamente válido. */
    fun openFollowUp(now: Long) {
        deadline = now + followUpMillis
    }

    fun close() {
        deadline = 0L
    }
}
