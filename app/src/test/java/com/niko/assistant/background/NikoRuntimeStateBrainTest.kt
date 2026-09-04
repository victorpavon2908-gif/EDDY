package com.niko.assistant.background

import org.junit.Assert.assertEquals
import org.junit.Test

class NikoRuntimeStateBrainTest {
    @Test fun brainProgressIsBoundedAndUsesRealBytes() {
        assertEquals(0, NikoRuntimeState.brainProgressPercent(0L, 205_980_058L))
        assertEquals(50, NikoRuntimeState.brainProgressPercent(102_990_029L, 205_980_058L))
        assertEquals(100, NikoRuntimeState.brainProgressPercent(205_980_058L, 205_980_058L))
        assertEquals(100, NikoRuntimeState.brainProgressPercent(300_000_000L, 205_980_058L))
        assertEquals(0, NikoRuntimeState.brainProgressPercent(50L, 0L))
    }
}
