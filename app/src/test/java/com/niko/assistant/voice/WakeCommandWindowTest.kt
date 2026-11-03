package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class WakeCommandWindowTest {
    @Test fun speechKeepsAnAuthorizedCommandAliveUntilItsEndpoint() {
        val window = WakeCommandWindow(durationMillis = 12_000L)
        window.onWake(1_000L)
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
        window.onWake(1_000L)
        window.onSpeech(13_000L)
        assertFalse(window.isOpen(13_001L))
    }

    @Test fun continuousSpeechStillHasAHardLimit() {
        val window = WakeCommandWindow(durationMillis = 12_000L)
        window.onWake(1_000L)
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

    @Test fun ordinaryResponseClosesCaptureAndRequiresAnotherWake() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        window.afterResponse(2_000L, continueCommand = false)
        window.onSpeech(2_100L)
        window.afterResponse(2_200L, continueCommand = true)
        assertFalse(window.isOpen(2_201L))
        window.onWake(3_000L)
        assertTrue(window.isOpen(3_001L))
    }

    @Test fun completedCommandCannotBeReopenedByTtsOrAPrompt() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        window.close()
        window.afterResponse(2_000L, continueCommand = false)
        window.afterResponse(2_500L, continueCommand = true)
        assertFalse(window.isOpen(2_501L))
    }

    @Test fun latePromptCannotReviveExpiredCaptureWithoutPolling() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        window.afterResponse(9_000L, continueCommand = true)
        assertFalse(window.isOpen(9_001L))
    }

    @Test fun silenceExpiresAndCannotBeExtendedByALatePrompt() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        assertTrue(window.isOpen(8_999L))
        assertFalse(window.isOpen(9_000L))
        window.continueAfterPrompt(10_000L)
        assertFalse(window.isOpen(10_001L))
    }

    @Test fun acknowledgementLeavesTimeForTheCommand() {
        val window = WakeCommandWindow()
        window.onWake(1_000L)
        window.afterResponse(5_000L, continueCommand = true)
        assertTrue(window.isOpen(12_999L))
        assertFalse(window.isOpen(13_000L))
    }
}
