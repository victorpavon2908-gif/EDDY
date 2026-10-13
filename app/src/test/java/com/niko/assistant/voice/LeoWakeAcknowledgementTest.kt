package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class LeoWakeAcknowledgementTest {
    @Test fun onlyStandaloneWakeGetsAnAcknowledgement() {
        assertTrue(LeoWakeAcknowledgement.shouldSpeak("", false))
        assertFalse(LeoWakeAcknowledgement.shouldSpeak("abrí Spotify", false))
        assertFalse(LeoWakeAcknowledgement.shouldSpeak(null, false))
        assertFalse(LeoWakeAcknowledgement.shouldSpeak("", true))
        assertFalse(LeoWakeAcknowledgement.shouldSpeak("pará", true))
    }
}
