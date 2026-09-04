package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechOutputPolicyTest {
    @Test fun readySystemVoiceStaysPreferredAcrossReplies() {
        val policy = SpeechOutputPolicy()
        assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(neuralAvailable = false, systemAvailable = true))
        assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(neuralAvailable = true, systemAvailable = true))
    }

    @Test fun failedNeuralVoiceDoesNotReturnOnEveryFollowingReply() {
        val policy = SpeechOutputPolicy()
        assertEquals(SpeechOutputPolicy.Backend.NEURAL, policy.choose(neuralAvailable = true, systemAvailable = false))
        policy.neuralFailed()
        repeat(5) { assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(neuralAvailable = true, systemAvailable = true)) }
    }

    @Test fun readySystemVoiceWinsTheFirstReplyForLowLatency() {
        val policy = SpeechOutputPolicy()
        assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(neuralAvailable = true, systemAvailable = true))
    }

    @Test fun unavailableSystemVoiceFallsBackToNeural() {
        val policy = SpeechOutputPolicy()
        assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(neuralAvailable = false, systemAvailable = true))
        policy.systemFailed()
        assertEquals(SpeechOutputPolicy.Backend.NEURAL, policy.choose(neuralAvailable = true, systemAvailable = false))
    }

    @Test fun lateReadySystemVoiceReplacesNeuralOnTheNextReply() {
        val policy = SpeechOutputPolicy()
        assertEquals(SpeechOutputPolicy.Backend.NEURAL, policy.choose(neuralAvailable = true, systemAvailable = false))
        assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(neuralAvailable = true, systemAvailable = true))
    }

    @Test fun failedSystemVoiceDoesNotBounceBackOnAStaleReadySignal() {
        val policy = SpeechOutputPolicy()
        assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(neuralAvailable = true, systemAvailable = true))
        policy.systemFailed()
        repeat(5) { assertEquals(SpeechOutputPolicy.Backend.NEURAL, policy.choose(neuralAvailable = true, systemAvailable = true)) }
    }
}
