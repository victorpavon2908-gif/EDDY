package com.eddy.assistant.voice

/** Keep one speaker for the service session, including after a neural synthesis failure. */
class SpeechOutputPolicy {
    enum class Backend { NEURAL, SYSTEM }
    @Volatile var selected: Backend? = null
        private set

    fun choose(neuralAvailable: Boolean): Backend = selected ?: (
        if (neuralAvailable) Backend.NEURAL else Backend.SYSTEM
    ).also { selected = it }

    fun neuralFailed() { selected = Backend.SYSTEM }
}
