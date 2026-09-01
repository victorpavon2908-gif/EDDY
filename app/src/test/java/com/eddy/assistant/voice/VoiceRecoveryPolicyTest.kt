package com.eddy.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceRecoveryPolicyTest {
    @Test fun persistentFailureUsesBoundedBackoffWithoutGivingUp() {
        val policy = VoiceRecoveryPolicy()
        assertEquals(listOf(5_000L, 15_000L, 60_000L, 300_000L, 300_000L),
            List(5) { policy.nextDelayMillis(it * 1_000L) })
    }

    @Test fun flappingCapturesDoNotResetBackoff() {
        val policy = VoiceRecoveryPolicy()
        policy.started(1_000L)
        assertEquals(5_000L, policy.nextDelayMillis(1_100L))
        policy.started(7_000L)
        assertEquals(15_000L, policy.nextDelayMillis(7_100L))
    }

    @Test fun healthyCaptureRestoresFastRecovery() {
        val policy = VoiceRecoveryPolicy()
        repeat(5) { policy.nextDelayMillis(0L) }
        policy.started(1_000L)
        assertEquals(5_000L, policy.nextDelayMillis(61_000L))
    }

    @Test fun nativeFailuresRepairOnlyTheAffectedModelOnce() {
        val policy = VoiceRecoveryPolicy()
        assertTrue(policy.allowModelRepair("kws"))
        assertFalse(policy.allowModelRepair("kws"))
        assertTrue(policy.allowModelRepair("asr"))
        policy.started(1_000L)
        policy.nextDelayMillis(61_000L)
        assertFalse(policy.allowModelRepair("kws"))
    }
}
