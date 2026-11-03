package com.niko.assistant.voice

/** Only an acoustic wake authorizes command capture; ordinary responses close it. */
class WakeCommandWindow(
    private val durationMillis: Long = 8_000L,
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
        if (isOpen(now)) onWake(now)
    }

    /** Only an acknowledgement/retry within the wake turn may keep capture open. */
    fun afterResponse(now: Long, continueCommand: Boolean) {
        if (continueCommand) continueAfterPrompt(now) else close()
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
