package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechStartGateTest {
    @Test fun lateNativeAudioCannotPlayAfterFallback() {
        val gate = SpeechStartGate()
        val slow = gate.begin()
        assertTrue(gate.expire(slow))
        assertFalse(gate.markStarted(slow))
        assertFalse(gate.current(slow))
        assertFalse(gate.expire(slow))
    }
    @Test fun speechAlreadyStartedMustNeverRestartFromBeginning() {
        val gate = SpeechStartGate()
        val token = gate.begin()
        assertTrue(gate.markStarted(token))
        assertFalse(gate.expire(token))
        assertTrue(gate.current(token))
    }
    @Test fun stoppingAndNewTurnInvalidateLateResultsAndCallbacks() {
        val gate = SpeechStartGate()
        val old = gate.begin()
        gate.cancel()
        assertFalse(gate.latest(old))
        val next = gate.begin()
        assertFalse(gate.markStarted(old))
        assertFalse(gate.expire(old))
        assertTrue(gate.markStarted(next))
    }
}
