package com.niko.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoPassiveWakeVerifierTest {
    @Test fun onlyShortSpeechBurstsAreProbed() {
        assertFalse(LeoPassiveWakeVerifier.shouldProbe(1_000, 2_000L, 0L))
        assertTrue(LeoPassiveWakeVerifier.shouldProbe(8_000, 2_000L, 0L))
        // La afinación 0.10.0 permite hasta ~3.2 s para rescatar "Leo" o "Leo + orden corta".
        assertTrue(LeoPassiveWakeVerifier.shouldProbe(50_000, 2_000L, 0L))
        assertFalse(LeoPassiveWakeVerifier.shouldProbe(64_000, 2_000L, 0L))
    }

    @Test fun probeHasCooldownToProtectBattery() {
        assertFalse(LeoPassiveWakeVerifier.shouldProbe(8_000, 1_500L, 1_000L))
        assertTrue(LeoPassiveWakeVerifier.shouldProbe(8_000, 2_100L, 1_000L))
    }

    @Test fun canaryTranscriptCanWakeLeoOrCarryInlineCommand() {
        assertEquals(WakeResult.Activated, LeoPassiveWakeVerifier.consumeTranscript("Leo", 1_000L))
        assertEquals(
            WakeResult.Command("abre YouTube"),
            LeoPassiveWakeVerifier.consumeTranscript("Leo, abre YouTube", 2_000L),
        )
    }

    @Test fun backgroundSpeechAndRetiredNameAreDiscarded() {
        assertEquals(WakeResult.Ignored, LeoPassiveWakeVerifier.consumeTranscript("quiero abrir YouTube", 1_000L))
        assertEquals(WakeResult.Ignored, LeoPassiveWakeVerifier.consumeTranscript("Niko abre YouTube", 2_000L))
    }
}
