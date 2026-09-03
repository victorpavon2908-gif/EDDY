package com.niko.assistant.voice

/**
 * Audio-worker state. Un wake acústico abre la primera orden; después de una respuesta
 * autorizada puede abrirse una ventana de seguimiento más corta para conversar sin
 * repetir NIKO en cada frase.
 */
class WakeCommandWindow(
    private val durationMillis: Long = 30_000L,
    private val followUpMillis: Long = 12_000L,
    private val maximumTurnMillis: Long = 60_000L,
) {
    private var deadline = 0L
    private var maximumDeadline = 0L

    fun onWake(now: Long) {
        deadline = now + durationMillis
        maximumDeadline = now + maxOf(durationMillis, maximumTurnMillis)
    }

    fun isOpen(now: Long): Boolean {
        if (now >= deadline) close()
        return deadline > 0L
    }

    /** Un aviso/reintento solo extiende una ventana que ya estaba autorizada. */
    fun continueAfterPrompt(now: Long) {
        if (deadline > 0L) onWake(now)
    }

    /** Solo el motor de voz debe llamarlo después de un wake acústico previamente válido. */
    fun openFollowUp(now: Long) {
        deadline = now + followUpMillis
        maximumDeadline = now + maxOf(followUpMillis, maximumTurnMillis)
    }

    /** VAD-confirmed speech keeps an already authorized turn alive through its endpoint. */
    fun onSpeech(now: Long) {
        if (!isOpen(now)) return
        deadline = maxOf(deadline, minOf(now + 1_800L, maximumDeadline))
    }

    fun close() {
        deadline = 0L
        maximumDeadline = 0L
    }
}
