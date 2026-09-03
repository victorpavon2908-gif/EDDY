package com.niko.assistant.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptLiteralSafetyTest {
    @Test fun differentContactNamesRequireRepetition() {
        assertTrue(
            TranscriptQuality.requiresClarification(
                "escribile a Juan que voy llegando",
                "escribile a Joaquín que voy llegando",
                48_000,
            ),
        )
    }

    @Test fun differentAddressesRequireRepetition() {
        assertTrue(
            TranscriptQuality.requiresClarification(
                "llevame a calle 12 casa 8",
                "llevame a calle 12 casa 9",
                48_000,
            ),
        )
    }

    @Test fun punctuationOnlyDoesNotChangeLiteralContent() {
        assertFalse(
            TranscriptQuality.requiresClarification(
                "mensaje a Ana: voy llegando",
                "mensaje a Ana voy llegando",
                48_000,
            ),
        )
    }
}
