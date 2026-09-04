package com.niko.assistant.voice

/** Prefer one low-latency speaker per session and switch only when that backend fails. */
class SpeechOutputPolicy {
    enum class Backend { NEURAL, SYSTEM }
    @Volatile var selected: Backend? = null
        private set

    /** Android TTS starts almost immediately; the heavier neural voice remains the fallback. */
    fun choose(neuralAvailable: Boolean, systemAvailable: Boolean): Backend = selected ?: when {
        systemAvailable -> Backend.SYSTEM
        neuralAvailable -> Backend.NEURAL
        else -> Backend.SYSTEM
    }.also { selected = it }

    fun neuralFailed() { selected = Backend.SYSTEM }
    fun systemFailed() { selected = Backend.NEURAL }
}
