package com.niko.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptQualityTest {
    @Test fun blankTranscriptRequestsWhisperRefinement() {
        assertTrue(TranscriptQuality.shouldRefine("", 16_000))
    }

    @Test fun cleanNaturalCommandStaysOnFastCanaryPath() {
        assertFalse(TranscriptQuality.shouldRefine("abrí YouTube", 32_000))
        assertFalse(TranscriptQuality.shouldRefine("apagála", 20_000))
    }

    @Test fun repeatedArtifactsRequestRefinement() {
        assertTrue(TranscriptQuality.shouldRefine("hola hola hola hola", 32_000))
        assertTrue(TranscriptQuality.shouldRefine("<unk>", 8_000))
    }

    @Test fun refinementWinsWhenPrimaryContainsArtifacts() {
        assertEquals(
            "prendé la linterna",
            TranscriptQuality.choose("<unk>", "prendé la linterna"),
        )
    }
}
