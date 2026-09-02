package com.niko.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoRealtimeTurnBusTest {
    @Test fun publishesBoundedLiveTranscript() {
        LeoRealtimeTurnBus.clearTranscript()
        LeoRealtimeTurnBus.updateTranscript("  Leo entendiendo mi frase  ")
        assertEquals("Leo entendiendo mi frase", LeoRealtimeTurnBus.liveTranscript.value)

        LeoRealtimeTurnBus.updateTranscript("x".repeat(500))
        assertEquals(320, LeoRealtimeTurnBus.liveTranscript.value.length)
        LeoRealtimeTurnBus.clearTranscript()
    }

    @Test fun wakeCanInterruptRegisteredSpeechOutputs() {
        var interrupted = 0
        val stopper: () -> Unit = { interrupted++ }
        LeoRealtimeTurnBus.registerSpeechStopper(stopper)
        try {
            LeoRealtimeTurnBus.interruptSpeech()
            assertEquals(1, interrupted)
        } finally {
            LeoRealtimeTurnBus.unregisterSpeechStopper(stopper)
        }
        assertTrue(LeoRealtimeTurnBus.liveTranscript.value.length <= 320)
    }
}
