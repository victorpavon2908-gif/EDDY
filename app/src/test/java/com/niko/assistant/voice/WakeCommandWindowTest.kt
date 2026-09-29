package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class WakeCommandWindowTest {
    @Test fun speechKeepsAnAuthorizedFollowUpAliveUntilItsEndpoint() {
        val window = WakeCommandWindow()
        window.openFollowUp(1_000L)
        window.onSpeech(12_900L)
        assertTrue(window.isOpen(13_100L))
        window.onSpeech(14_000L)
        assertTrue(window.isOpen(15_799L))
        assertFalse(window.isOpen(15_800L))
    }

    @Test fun speechCannotAuthorizeOrResurrectAClosedWindow() {
        val window = WakeCommandWindow()
        window.onSpeech(500L)
        assertFalse(window.isOpen(501L))
        window.openFollowUp(1_000L)
        window.onSpeech(13_000L)
        assertFalse(window.isOpen(13_001L))
    }

    @Test fun continuousSpeechStillHasAHardLimit() {
        val window = WakeCommandWindow()
        window.openFollowUp(1_000L)
        for (now in 12_000L..60_000L step 1_000L) window.onSpeech(now)
        assertTrue(window.isOpen(60_999L))
        window.onSpeech(61_000L)
        assertFalse(window.isOpen(61_001L))
    }

    @Test fun startupAndTtsAloneCannotAuthorizeACommand() {
        val window = WakeCommandWindow()
        assertFalse(window.isOpen(1_000L))
        window.continueAfterPrompt(2_000L)
        assertFalse(window.isOpen(2_001L))
    }

    @Test fun acousticWakeAllowsFirstTurn() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        assertTrue(window.isOpen(1_001L))
        window.close()
        window.continueAfterPrompt(2_000L)
        assertFalse(window.isOpen(2_001L))
    }

    @Test fun authorizedFollowUpIsShorterThanInitialWake() {
        val window = WakeCommandWindow(durationMillis = 30_000L, followUpMillis = 12_000L)
        window.onWake(1_000L)
        window.close()
        window.openFollowUp(5_000L)
        assertTrue(window.isOpen(16_999L))
        assertFalse(window.isOpen(17_000L))
    }

    @Test fun silenceExpiresAndCannotBeExtendedByALatePrompt() {
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
