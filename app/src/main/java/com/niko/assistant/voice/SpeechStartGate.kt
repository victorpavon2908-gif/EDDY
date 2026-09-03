package com.niko.assistant.voice

/** Arbitrates the first audio against fallback/stop, including late native synthesis. */
internal class SpeechStartGate {
    private var epoch = 0L
    private var active = false
    private var started = false

    @Synchronized fun begin(): Long { active = true; started = false; return ++epoch }
    @Synchronized fun current(token: Long) = active && token == epoch
    @Synchronized fun latest(token: Long) = token == epoch
    @Synchronized fun markStarted(token: Long): Boolean {
        if (!current(token)) return false
        started = true
        return true
    }
    @Synchronized fun expire(token: Long): Boolean {
        if (!current(token) || started) return false
        active = false
        return true
    }
    @Synchronized fun cancel(): Long { active = false; return ++epoch }
}
