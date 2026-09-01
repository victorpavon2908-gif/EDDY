package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class WakeCommandWindowTest {
    @Test fun startupAndTtsAloneCannotAuthorizeACommand() {
        val window = WakeCommandWindow()
        assertFalse(window.isOpen(1_000L))
        window.continueAfterPrompt(2_000L)
        assertFalse(window.isOpen(2_001L))
    }

    @Test fun acousticWakeAllowsOneTurnThenRequiresAnotherWake() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        assertTrue(window.isOpen(1_001L))
        window.close()
        window.continueAfterPrompt(2_000L)
        assertFalse(window.isOpen(2_001L))
        window.onWake(3_000L)
        assertTrue(window.isOpen(3_001L))
    }

    @Test fun silenceExpiresAndCannotBeReopenedByALateSpeechCallback() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        assertTrue(window.isOpen(30_999L))
        assertFalse(window.isOpen(31_000L))
        window.continueAfterPrompt(32_000L)
        assertFalse(window.isOpen(32_001L))
    }

    @Test fun acknowledgementLeavesTimeForTheCommand() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        window.continueAfterPrompt(5_000L)
        assertTrue(window.isOpen(34_999L))
        assertFalse(window.isOpen(35_000L))
    }
}
