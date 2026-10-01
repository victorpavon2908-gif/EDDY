package com.niko.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeoPlatformVoiceEngineTest {
    @Test fun detectsWakeWordAlone() {
        assertEquals("", LeoPlatformVoiceEngine.extractWakeCommand("Leo"))
        assertEquals("", LeoPlatformVoiceEngine.extractWakeCommand("¡LEO!"))
    }

    @Test fun extractsInlineCommandAfterWakeWord() {
        assertEquals("enciende la linterna", LeoPlatformVoiceEngine.extractWakeCommand("Leo, enciende la linterna"))
        assertEquals("qué hora es", LeoPlatformVoiceEngine.extractWakeCommand("oye Leo qué hora es"))
    }

    @Test fun doesNotMatchSimilarWords() {
        assertNull(LeoPlatformVoiceEngine.extractWakeCommand("leon"))
        assertNull(LeoPlatformVoiceEngine.extractWakeCommand("galileo"))
        assertNull(LeoPlatformVoiceEngine.extractWakeCommand("empleo"))
    }
}
