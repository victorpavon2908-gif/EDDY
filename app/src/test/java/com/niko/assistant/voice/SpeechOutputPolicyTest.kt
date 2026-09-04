package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechOutputPolicyTest {
    @Test fun installingAVoiceDoesNotChangeTheSpeakerMidSession() {
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
}
