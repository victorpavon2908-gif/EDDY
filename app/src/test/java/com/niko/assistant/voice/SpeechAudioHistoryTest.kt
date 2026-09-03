package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechAudioHistoryTest {
    @Test fun wakePreRollIsFedIn512SampleFramesWithoutLosingItsFirstWords() {
        val history = SpeechAudioHistory(capacity = 2_000, paddingBefore = 2, paddingAfter = 2)
        val samples = FloatArray(1_640) { it.toFloat() }
        val frames = mutableListOf<FloatArray>()
        history.feed(samples, frames::add)
        assertEquals(listOf(512, 512, 512, 104), frames.map { it.size })
        assertArrayEquals(samples, frames.flatMap { it.asList() }.toFloatArray(), 0f)
        assertArrayEquals(floatArrayOf(510f, 511f, 512f, 513f, 514f),
            history.withContext(512, floatArrayOf(512f)), 0f)
    }

    @Test fun restoresRealLeadingAndTrailingSamplesWithoutDuplicatingTheSegment() {
        val history = SpeechAudioHistory(capacity = 20, paddingBefore = 2, paddingAfter = 3)
        history.append(FloatArray(12) { it.toFloat() })
        assertArrayEquals(floatArrayOf(2f, 3f, 4f, 5f, 6f, 7f, 8f),
            history.withContext(4, floatArrayOf(4f, 5f)), 0f)
    }

    @Test fun paddingIsClampedToAvailableAudioAndNeverFabricated() {
        val history = SpeechAudioHistory(capacity = 10, paddingBefore = 8, paddingAfter = 8)
        history.append(floatArrayOf(1f, 2f, 3f))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), history.withContext(0, floatArrayOf(1f, 2f)), 0f)
    }

    @Test fun circularWrapPreservesSampleOrder() {
        val history = SpeechAudioHistory(capacity = 8, paddingBefore = 2, paddingAfter = 2)
        history.append(FloatArray(14) { it.toFloat() })
        assertArrayEquals(floatArrayOf(6f, 7f, 8f, 9f, 10f, 11f),
            history.withContext(8, floatArrayOf(8f, 9f)), 0f)
    }

    @Test fun resetCannotBringBackAudioFromAPreviousTurn() {
        val history = SpeechAudioHistory(capacity = 8, paddingBefore = 2, paddingAfter = 2)
        history.append(FloatArray(8) { 99f })
        history.clear()
        history.append(floatArrayOf(1f, 2f, 3f))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), history.withContext(1, floatArrayOf(2f)), 0f)
    }

    @Test fun staleOrInvalidOffsetsPreserveTheOriginalSegment() {
        val history = SpeechAudioHistory(capacity = 4)
        history.append(FloatArray(10) { it.toFloat() })
        val segment = floatArrayOf(1f, 2f)
        assertSame(segment, history.withContext(1, segment))
        assertSame(segment, history.withContext(-1, segment))
        assertSame(segment, history.withContext(10, segment))
    }
}
