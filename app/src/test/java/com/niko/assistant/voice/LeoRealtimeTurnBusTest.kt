package com.niko.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class LeoRealtimeTurnBusTest {
    @Test fun interruptionCancelsTheProducerBeforeNotifyingPlayback() {
        val events = mutableListOf<String>()
        val producer: () -> Unit = { events.add("cancel search"); Unit }
        val broken: () -> Unit = { error("one failed listener must not block stop") }
        val player: () -> Unit = { events.add("stop audio"); Unit }
        LeoRealtimeTurnBus.registerTurnInterrupter(producer)
        LeoRealtimeTurnBus.registerSpeechStopper(broken)
        LeoRealtimeTurnBus.registerSpeechStopper(player)
        try {
            LeoRealtimeTurnBus.interruptTurn()
            assertEquals(listOf("cancel search", "stop audio"), events)
        } finally {
            LeoRealtimeTurnBus.unregisterTurnInterrupter(producer)
            LeoRealtimeTurnBus.unregisterSpeechStopper(broken)
            LeoRealtimeTurnBus.unregisterSpeechStopper(player)
        }
    }
    @Test fun publishesTheWholeTranscriptWithoutDroppingWordsAfter320Characters() {
        LeoRealtimeTurnBus.clearTranscript()
        LeoRealtimeTurnBus.updateTranscript("  Leo entendiendo mi frase  ")
        assertEquals("Leo entendiendo mi frase", LeoRealtimeTurnBus.liveTranscript.value)

        LeoRealtimeTurnBus.updateTranscript("x".repeat(500))
        assertEquals(500, LeoRealtimeTurnBus.liveTranscript.value.length)
        LeoRealtimeTurnBus.clearTranscript()
    }

    @Test fun latePreviewCannotOverwriteFinalWordsOrTheNextTurn() {
        LeoRealtimeTurnBus.clearTranscript()
        val token = LeoRealtimeTurnBus.previewToken()
        LeoRealtimeTurnBus.updatePreview("llamá", token)
        assertEquals("llamá", LeoRealtimeTurnBus.liveTranscript.value)
        LeoRealtimeTurnBus.updateTranscript("no llamés a Nico")
        LeoRealtimeTurnBus.updatePreview("llamá a Nico", token)
        assertEquals("no llamés a Nico", LeoRealtimeTurnBus.liveTranscript.value)
        val next = LeoRealtimeTurnBus.previewToken()
        LeoRealtimeTurnBus.clearTranscript()
        LeoRealtimeTurnBus.updatePreview("audio anterior", next)
        assertEquals("", LeoRealtimeTurnBus.liveTranscript.value)
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
        LeoRealtimeTurnBus.clearTranscript()
    }
}
