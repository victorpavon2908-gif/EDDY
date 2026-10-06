package com.niko.assistant.proactive

import org.junit.Assert.*
import org.junit.Test

class LeoInitiativeEngineTest {
    private fun candidate(id: String = "habit", reason: String = "3 solicitudes observadas") =
        LeoInitiativeEngine.Candidate(id, "¿Abrimos música?", reason, 40, 0.8, Long.MAX_VALUE)
    private fun offer(engine: LeoInitiativeEngine, now: Long = 0, hour: Int = 12, candidates: List<LeoInitiativeEngine.Candidate> = listOf(candidate())): LeoInitiativeEngine.Candidate? {
        engine.decide(now, "day", hour, true, true, candidates)
        return engine.decide(now + 46_000, "day", hour, true, true, candidates)
    }
    @Test fun requiresEvidence() { assertNull(offer(LeoInitiativeEngine(), candidates = listOf(candidate(reason = "")))) }
    @Test fun noGenericInvitationWithoutCandidates() { assertNull(offer(LeoInitiativeEngine(), candidates = emptyList())) }
    @Test fun quietHoursSuppress() { assertNull(offer(LeoInitiativeEngine(), hour = 23)) }
    @Test fun disabledSuppresses() {
        val engine = LeoInitiativeEngine(); assertNull(engine.decide(100_000, "day", 12, false, true, listOf(candidate())))
    }
    @Test fun busySuppresses() {
        val engine = LeoInitiativeEngine(); assertNull(engine.decide(100_000, "day", 12, true, false, listOf(candidate())))
    }
    @Test fun offersReasonAndPersistsLimit() {
        val engine = LeoInitiativeEngine(); assertNotNull(offer(engine))
        val restored = LeoInitiativeEngine(); restored.restore(engine.state)
        assertEquals(1, restored.state.count); assertNotNull(restored.state.history.single().reason)
        assertNull(offer(restored, now = 60_000))
    }
    @Test fun ignoredCandidateGetsWeekSuppression() {
        val engine = LeoInitiativeEngine(); offer(engine)
        engine.decide(200_000, "day", 12, true, true, listOf(candidate()))
        assertEquals("IGNORED", engine.state.history.single().outcome)
        assertTrue(engine.state.suppressed.getValue("habit") >= 7 * 24 * 60 * 60_000L)
    }
    @Test fun rejectedCandidateIsNotReofferedOnNextDay() {
        val engine = LeoInitiativeEngine(); offer(engine); engine.interacted(50_000, rejected = true)
        assertEquals("REJECTED", engine.state.history.single().outcome)
        engine.decide(86_400_000, "next day", 12, true, true, listOf(candidate()))
        assertNull(engine.decide(86_500_000, "next day", 12, true, true, listOf(candidate())))
    }
    @Test fun dailyLimitIsNotResetByInteraction() {
        val engine = LeoInitiativeEngine(); offer(engine); engine.interacted(50_000)
        offer(engine, now = 2_000_000, candidates = listOf(candidate("second"))); engine.interacted(2_050_000)
        assertEquals(2, engine.state.count)
        assertNull(offer(engine, now = 4_000_000, candidates = listOf(candidate("third"))))
    }
}
