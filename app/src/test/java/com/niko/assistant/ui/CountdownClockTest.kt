package com.niko.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownClockTest {
    @Test fun delayedFrameIncludesAllElapsedTime() {
        assertEquals(47_500L, CountdownClock.remaining(60_000L, 12_500L))
    }
    @Test fun backgroundReturnAfterDeadlineCompletes() {
        assertEquals(0L, CountdownClock.remaining(60_000L, 90_000L))
    }
    @Test fun exactDeadlineIsZero() {
        assertEquals(0L, CountdownClock.remaining(60_000L, 60_000L))
    }
}
