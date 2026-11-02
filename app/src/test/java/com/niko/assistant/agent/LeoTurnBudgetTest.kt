package com.niko.assistant.agent

import com.niko.assistant.modes.LeoMode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class LeoTurnBudgetTest {
    @Test fun stagesShareTheRemainingTimeAndKeepFallbackReserve() = runBlocking {
        var time = 0L
        val budget = LeoTurnBudget(40_000) { time }
        withContext(budget) {
            assertEquals(20_000L, LeoTurnBudget.limit(20_000))
            time += 30_000_000_000L
            assertEquals(7_000L, LeoTurnBudget.limit(185_000))
            time += 8_000_000_000L
            assertEquals(0L, LeoTurnBudget.limit(10_000))
        }
        assertEquals(185_000L, LeoTurnBudget.limit(185_000))
    }
    @Test fun commandDeadlineAccommodatesEachModeProducerAndReply() {
        for (mode in LeoMode.entries) {
            assertTrue(mode.commandTimeoutMs > mode.inferenceTimeoutMs + mode.searchMs)
        }
        assertTrue(LeoMode.SUPER.commandTimeoutMs < LeoMode.NORMAL.commandTimeoutMs)
    }
}
