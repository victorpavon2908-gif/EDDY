package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class OwnerVoicePolicyTest {
    private val owner = floatArrayOf(1f, 0f, 0f)
    @Test fun acceptsConsistentVoiceWithoutDependingOnVolume() {
        assertTrue(OwnerVoicePolicy.accepts(owner, listOf(floatArrayOf(0.2f, 0.01f, 0f), floatArrayOf(2f, 0f, 0f))))
    }
    @Test fun rejectsDifferentSpeakerEvenAfterOwnerStartedTheTurn() {
        assertFalse(OwnerVoicePolicy.accepts(owner, listOf(owner, floatArrayOf(0f, 1f, 0f))))
    }
    @Test fun ambiguousOrMissingEmbeddingsDoNotAuthorizeACommand() {
        listOf(floatArrayOf(), floatArrayOf(0f, 0f, 0f), floatArrayOf(Float.NaN, 0f, 0f), floatArrayOf(Float.POSITIVE_INFINITY, 0f, 0f), floatArrayOf(1f)).forEach {
            assertFalse(OwnerVoicePolicy.accepts(owner, listOf(it)))
        }
        assertFalse(OwnerVoicePolicy.accepts(owner, emptyList()))
        assertFalse(OwnerVoicePolicy.accepts(owner, listOf(floatArrayOf(0.5f, 0.8f, 0.2f))))
    }
}
