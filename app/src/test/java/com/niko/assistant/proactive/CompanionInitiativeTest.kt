package com.niko.assistant.proactive

import org.junit.Assert.*
import org.junit.Test

class CompanionInitiativeTest {
    @Test fun onlyInvitesAfterContinuousAvailableIdle() {
        val policy = CompanionInitiative()
        assertNull(policy.invitation(0, true, true))
        assertNull(policy.invitation(40000, true, false))
        assertNull(policy.invitation(50000, true, true))
        assertNull(policy.invitation(94000, true, true))
        assertNotNull(policy.invitation(95000, true, true))
    }
    @Test fun stopsAfterTwoUnansweredInvitations() {
        val policy = CompanionInitiative()
        policy.invitation(0, true, true)
        assertNotNull(policy.invitation(45000, true, true))
        assertNull(policy.invitation(344999, true, true))
        assertNull(policy.invitation(345000, true, true))
        assertNotNull(policy.invitation(390000, true, true))
        assertNull(policy.invitation(9999999, true, true))
    }
    @Test fun disabledAndSilenceNeverTriggerSpeech() {
        val policy = CompanionInitiative()
        assertNull(policy.invitation(0, false, true))
        assertNull(policy.invitation(999999, false, true))
        policy.silence(1000000)
        assertNull(policy.invitation(9999999, true, true))
    }
    @Test fun userInteractionCreatesACooldownAndResetsBudget() {
        val policy = CompanionInitiative()
        policy.interacted(1000)
        assertNull(policy.invitation(90000, true, true))
        assertNull(policy.invitation(91000, true, true))
        assertNotNull(policy.invitation(136000, true, true))
    }
}
