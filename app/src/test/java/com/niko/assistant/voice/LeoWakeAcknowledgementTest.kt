package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class LeoWakeAcknowledgementTest {
    @Test fun localWakeAcknowledgesOnlyAfterQuietTimeout() {
        fun canSpeak(
            ageMs: Long = 1_200L,
            acknowledged: Boolean = false,
            started: Boolean = false,
            vadSpeech: Boolean = false,
            speaking: Boolean = false,
        ) = LeoWakeAcknowledgement.shouldPromptAfterLocalWake(
            ageMs, acknowledged, started, vadSpeech, speaking,
        )
        assertFalse(canSpeak(ageMs = 1_199L))
        assertTrue(canSpeak(ageMs = 1_200L))
        assertFalse(canSpeak(acknowledged = true))
        assertFalse(canSpeak(started = true))
        assertFalse(canSpeak(vadSpeech = true))
        assertFalse(canSpeak(speaking = true))
    }

    @Test fun onlyStandaloneWakeGetsAnAcknowledgement() {
        assertTrue(LeoWakeAcknowledgement.shouldSpeak("", false))
        assertFalse(LeoWakeAcknowledgement.shouldSpeak("abrí Spotify", false))
        assertFalse(LeoWakeAcknowledgement.shouldSpeak(null, false))
        assertFalse(LeoWakeAcknowledgement.shouldSpeak("", true))
        assertFalse(LeoWakeAcknowledgement.shouldSpeak("pará", true))
    }
}
