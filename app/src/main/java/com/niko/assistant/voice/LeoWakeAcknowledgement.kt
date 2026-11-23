package com.niko.assistant.voice

/** Speak only after a final, standalone wake word, never over an inline command or barge-in. */
internal object LeoWakeAcknowledgement {
    fun shouldSpeak(finalWakeCommand: String?, interrupted: Boolean): Boolean =
        finalWakeCommand == "" && !interrupted

    /** Only used after a KWS wake, when no command has begun and the VAD is quiet. */
    fun shouldPromptAfterLocalWake(
        elapsedSinceWakeMs: Long,
        alreadyAcknowledged: Boolean,
        commandSpeechStarted: Boolean,
        speechInProgress: Boolean,
        assistantSpeaking: Boolean,
    ): Boolean = elapsedSinceWakeMs >= LOCAL_WAKE_ACK_DELAY_MS &&
        !alreadyAcknowledged && !commandSpeechStarted && !speechInProgress && !assistantSpeaking

    private const val LOCAL_WAKE_ACK_DELAY_MS = 1_200L
}
