package com.eddy.assistant.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PcmPreRollTest {
    @Test fun keepsLatestAudioInChronologicalOrderAcrossWraparound() {
        val buffer = PcmPreRoll(4)
        buffer.append(floatArrayOf(1f, 2f, 3f))
        buffer.append(floatArrayOf(4f, 5f, 6f))
        assertArrayEquals(floatArrayOf(3f, 4f, 5f, 6f), buffer.snapshot(), 0f)
    }
    @Test fun clearsPreviousUtteranceAndDoesNotExposeInternalBuffer() {
        val buffer = PcmPreRoll(4)
        buffer.append(floatArrayOf(1f, 2f))
        buffer.snapshot()[0] = 99f
        assertArrayEquals(floatArrayOf(1f, 2f), buffer.snapshot(), 0f)
        buffer.clear()
        buffer.append(floatArrayOf(3f))
        assertArrayEquals(floatArrayOf(3f), buffer.snapshot(), 0f)
    }
}
