package com.niko.assistant.voice

/** Prefer the low-latency Android voice as soon as it is ready, without retrying failed backends. */
class SpeechOutputPolicy {
    enum class Backend { NEURAL, SYSTEM }

    @Volatile var selected: Backend? = null
        private set
    @Volatile private var systemRejected = false
    @Volatile private var neuralRejected = false

    /**
     * Android TTS can finish initializing after the first reply. Upgrade to it on the next
     * reply when it becomes ready, but never bounce back to a backend that already failed.
     */
    @Synchronized
    fun choose(neuralAvailable: Boolean, systemAvailable: Boolean): Backend {
        val backend = when {
            systemAvailable && !systemRejected -> Backend.SYSTEM
            neuralAvailable && !neuralRejected -> Backend.NEURAL
            selected != null -> selected!!
            systemAvailable -> Backend.SYSTEM
            neuralAvailable -> Backend.NEURAL
            else -> Backend.SYSTEM
        }
        selected = backend
        return backend
    }

    @Synchronized
    fun neuralFailed() {
        neuralRejected = true
        selected = Backend.SYSTEM
    }

    @Synchronized
    fun systemFailed() {
        systemRejected = true
        selected = Backend.NEURAL
    }
}
