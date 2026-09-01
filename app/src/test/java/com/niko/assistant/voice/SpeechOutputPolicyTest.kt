package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechOutputPolicyTest {
    @Test fun installingAVoiceDoesNotChangeTheSpeakerMidSession() {
        val policy = SpeechOutputPolicy()
        assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(false))
        assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(true))
    }

    @Test fun failedNeuralVoiceDoesNotReturnOnEveryFollowingReply() {
        val policy = SpeechOutputPolicy()
        assertEquals(SpeechOutputPolicy.Backend.NEURAL, policy.choose(true))
        policy.neuralFailed()
        repeat(5) { assertEquals(SpeechOutputPolicy.Backend.SYSTEM, policy.choose(true)) }
    }
}
