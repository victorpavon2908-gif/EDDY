package com.eddy.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordGateTest {
    @Test
    fun exactWakeWordActivates() {
        val gate = WakeWordGate()
        assertEquals(WakeResult.Activated, gate.consume("EDDY", nowMs = 1_000L))
    }

    @Test
    fun wakeWordWithCommandReturnsCommand() {
        val gate = WakeWordGate()
        assertEquals(
            WakeResult.Command("abre YouTube"),
            gate.consume("EDDY, abre YouTube", nowMs = 1_000L),
        )
    }

    @Test
    fun freddyDoesNotActivateEddy() {
        val gate = WakeWordGate()
        assertEquals(
            WakeResult.Ignored,
            gate.consume("Freddy abre YouTube", nowMs = 1_000L),
        )
    }

    @Test
    fun partialWakeWordIsDetectedWithoutFalsePositive() {
        val gate = WakeWordGate()
        assertTrue(gate.hasWakeWord("hola EDDY"))
        assertTrue(gate.hasWakeWord("EDDY abre la cámara"))
        assertFalse(gate.hasWakeWord("Freddy abre YouTube"))
        assertFalse(gate.hasWakeWord("eddyson"))
    }

    @Test
    fun followUpCommandWorksInsideWindow() {
        val gate = WakeWordGate(followUpWindowMs = 12_000L)
        assertEquals(WakeResult.Activated, gate.consume("EDDY", nowMs = 1_000L))
        assertEquals(
            WakeResult.Command("abre la cámara"),
            gate.consume("abre la cámara", nowMs = 5_000L),
        )
    }

    @Test
    fun followUpExpiresOutsideWindow() {
        val gate = WakeWordGate(followUpWindowMs = 2_000L)
        assertEquals(WakeResult.Activated, gate.consume("EDDY", nowMs = 1_000L))
        assertEquals(
            WakeResult.Ignored,
            gate.consume("abre la cámara", nowMs = 3_500L),
        )
    }
}
