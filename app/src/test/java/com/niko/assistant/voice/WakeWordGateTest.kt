package com.niko.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordGateTest {
    @Test
    fun armedContinuationAuthorizesExactlyOneCommand() {
        val gate = WakeWordGate()
        gate.arm(nowMs = 1_000L, durationMs = 30_000L)
        assertEquals(WakeResult.Command("qué hora es"), gate.consume("qué hora es", 2_000L))
        assertEquals(WakeResult.Ignored, gate.consume("abrí YouTube", 3_000L))
        gate.arm(nowMs = 4_000L, durationMs = 30_000L)
        assertEquals(WakeResult.Ignored, gate.consume("abrí YouTube", 34_001L))
    }

    @Test
    fun exactWakeWordActivates() {
        val gate = WakeWordGate()
        assertEquals(WakeResult.Activated, gate.consume("NIKO", nowMs = 1_000L))
    }

    @Test
    fun commonSpeechAliasesActivate() {
        val gate = WakeWordGate()
        for ((index, alias) in listOf("Nico", "Niko", "Nikko", "Nin").withIndex()) {
            gate.disarm()
            assertEquals(
                "No activó con $alias",
                WakeResult.Activated,
                gate.consume(alias, nowMs = 1_000L + index),
            )
        }
    }

    @Test
    fun aliasesCanPrefixCommands() {
        val gate = WakeWordGate()
        assertEquals(
            WakeResult.Command("abre YouTube"),
            gate.consume("Nin, abre YouTube", nowMs = 1_000L),
        )
        assertEquals(
            WakeResult.Command("prende la linterna"),
            gate.consume("Hey Nico prende la linterna", nowMs = 2_000L),
        )
    }

    @Test
    fun wakeWordWithCommandReturnsCommand() {
        val gate = WakeWordGate()
        assertEquals(
            WakeResult.Command("abre YouTube"),
            gate.consume("NIKO, abre YouTube", nowMs = 1_000L),
        )
        assertFalse(gate.isArmed(nowMs = 5_000L))
    }

    @Test
    fun ordinaryMentionDoesNotActivate() {
        val gate = WakeWordGate()
        assertEquals(WakeResult.Ignored, gate.consume("ayer hablé con Niko sobre esto", 1_000L))
        assertEquals(WakeResult.Command("abrí cámara"), gate.consume("Hola, NIKO, abrí cámara", 2_000L))
    }

    @Test
    fun commandConsumesWakeAndBackgroundSpeechIsIgnored() {
        val gate = WakeWordGate()
        assertEquals(WakeResult.Activated, gate.consume("NIKO", 1_000L))
        assertEquals(WakeResult.Command("qué hora es"), gate.consume("qué hora es", 2_000L))
        assertEquals(WakeResult.Ignored, gate.consume("abrí cámara", 3_000L))
        assertEquals(WakeResult.Command("abrí cámara"), gate.consume("NIKO abrí cámara", 4_000L))
        assertFalse(gate.isArmed(4_001L))
    }

    @Test
    fun similarNamesDoNotActivateNiko() {
        val gate = WakeWordGate()
        for (phrase in listOf("Nicolás abre YouTube", "Nicole abre YouTube", "único abre YouTube")) {
            assertEquals(WakeResult.Ignored, gate.consume(phrase, nowMs = 1_000L))
        }
    }

    @Test
    fun partialWakeWordIsDetectedWithoutFalsePositive() {
        val gate = WakeWordGate()
        assertTrue(gate.hasWakeWord("hola NIKO"))
        assertTrue(gate.hasWakeWord("Nico abre la cámara"))
        assertTrue(gate.hasWakeWord("Nin abre la cámara"))
        assertFalse(gate.hasWakeWord("Nicole abre YouTube"))
        assertFalse(gate.hasWakeWord("Nicolás"))
        assertFalse(gate.hasWakeWord("ni abras la cámara"))
    }

    @Test fun previousNameAndItsAliasesNoLongerActivate() {
        for (name in listOf("Eddy", "Edi", "Edy", "Eddie", "Eddi")) {
            val gate = WakeWordGate()
            assertEquals(WakeResult.Ignored, gate.consume("$name, abrí cámara", 1_000L))
            assertFalse(gate.isArmed(1_001L))
        }
    }

    @Test
    fun partialDetectionCanArmNextCommand() {
        val gate = WakeWordGate(followUpWindowMs = 20_000L, conversationWindowMs = 12_000L)
        gate.arm(nowMs = 1_000L)
        assertTrue(gate.isArmed(nowMs = 5_000L))
        assertEquals(
            WakeResult.Command("abre calculadora"),
            gate.consume("abre calculadora", nowMs = 5_000L),
        )
        assertTrue(gate.isArmed(nowMs = 5_001L))
        assertTrue(gate.isArmed(nowMs = 16_999L))
        assertFalse(gate.isArmed(nowMs = 17_001L))
    }

    @Test
    fun multipleFollowUpsWorkInsideConversationWindow() {
        val gate = WakeWordGate(followUpWindowMs = 20_000L, conversationWindowMs = 12_000L)
        assertEquals(WakeResult.Activated, gate.consume("NIKO", nowMs = 1_000L))
        assertEquals(
            WakeResult.Command("abre la cámara"),
            gate.consume("abre la cámara", nowMs = 5_000L),
        )
        assertEquals(
            WakeResult.Command("ahora prende la linterna"),
            gate.consume("ahora prende la linterna", nowMs = 12_000L),
        )
    }

    @Test
    fun followUpExpiresOutsideWindow() {
        val gate = WakeWordGate(followUpWindowMs = 2_000L, conversationWindowMs = 1_000L)
        assertEquals(WakeResult.Activated, gate.consume("NIKO", nowMs = 1_000L))
        assertEquals(
            WakeResult.Ignored,
            gate.consume("abre la cámara", nowMs = 3_500L),
        )
    }
}
