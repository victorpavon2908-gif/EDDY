package com.niko.assistant.media

/** Main-thread ownership: the assistant and a recorder cannot share the microphone. */
object CaptureGate {
    private var owner: Any? = null
    val held: Boolean get() = owner != null
    var pauseVoice: (suspend () -> Boolean)? = null
    var resumeVoice: (() -> Unit)? = null

    suspend fun acquire(token: Any): Boolean {
        if (owner != null) return false
        owner = token
        return try {
            if (pauseVoice?.invoke() != false) true else { release(token); false }
        } catch (error: Throwable) {
            release(token)
            throw error
        }
    }

    fun release(token: Any) {
        if (owner !== token) return
        owner = null
        resumeVoice?.invoke()
    }
}
