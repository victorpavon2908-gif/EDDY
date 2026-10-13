package com.niko.assistant.voice

/** Speak only after a final, standalone wake word, never over an inline command or barge-in. */
internal object LeoWakeAcknowledgement {
    fun shouldSpeak(finalWakeCommand: String?, interrupted: Boolean): Boolean =
        finalWakeCommand == "" && !interrupted
}
