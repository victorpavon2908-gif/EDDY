package com.niko.assistant.voice

import kotlin.math.sqrt
import org.junit.Assert.*
import org.junit.Test

class OwnerVoicePolicyTest {
    private val owner = floatArrayOf(1f, 0f, 0f)

    private fun cosineVector(score: Float): FloatArray {
        val clamped = score.coerceIn(-1f, 1f)
        return floatArrayOf(clamped, sqrt((1f - clamped * clamped).coerceAtLeast(0f)), 0f)
    }

    @Test fun acceptsConsistentVoiceWithoutDependingOnVolume() {
        assertTrue(OwnerVoicePolicy.accepts(owner, listOf(floatArrayOf(0.2f, 0.01f, 0f), floatArrayOf(2f, 0f, 0f))))
    }

    @Test fun oneModeratelyNoisyWindowDoesNotRejectAnOtherwiseStrongOwnerTurn() {
        val result = OwnerVoicePolicy.evaluate(
            listOf(owner),
            listOf(owner, cosineVector(0.47f), cosineVector(0.58f)),
        )
        assertTrue(result.accepted)
        assertTrue(result.confidence >= OwnerVoicePolicy.ACCEPTANCE_THRESHOLD)
    }

    @Test fun rejectsDifferentSpeakerEvenAfterOwnerStartedTheTurn() {
        assertFalse(OwnerVoicePolicy.accepts(owner, listOf(owner, floatArrayOf(0f, 1f, 0f))))
        assertFalse(OwnerVoicePolicy.accepts(owner, listOf(owner, cosineVector(0.15f), owner)))
    }

    @Test fun explicitTemplatesCanMatchAValidOwnerVariationBetterThanTheCentroid() {
        val alternateOwnerTemplate = cosineVector(0.60f)
        val spoken = alternateOwnerTemplate.copyOf()
        val match = OwnerVoicePolicy.evaluate(listOf(owner, alternateOwnerTemplate), listOf(spoken))
        assertTrue(match.accepted)
        assertEquals(1f, match.confidence, 0.0001f)
    }

    @Test fun enrollmentAllowsNormalVariationButRejectsAClearSpeakerSwap() {
        val existing = listOf(owner, cosineVector(0.82f))
        assertTrue(OwnerVoicePolicy.enrollmentConsistent(existing, cosineVector(0.72f)))
        assertFalse(OwnerVoicePolicy.enrollmentConsistent(existing, cosineVector(0.20f)))
    }

    @Test fun ambiguousOrMissingEmbeddingsDoNotAuthorizeACommand() {
        listOf(
            floatArrayOf(),
            floatArrayOf(0f, 0f, 0f),
            floatArrayOf(Float.NaN, 0f, 0f),
            floatArrayOf(Float.POSITIVE_INFINITY, 0f, 0f),
            floatArrayOf(1f),
        ).forEach {
            assertFalse(OwnerVoicePolicy.accepts(owner, listOf(it)))
        }
        assertFalse(OwnerVoicePolicy.accepts(owner, emptyList()))
        assertFalse(OwnerVoicePolicy.accepts(owner, listOf(floatArrayOf(0.5f, 0.8f, 0.2f))))
    }
}
