package com.niko.assistant.voice

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FarFieldAudioEnhancerTest {
    @Test
    fun distantSpeechGetsMoreGainDuringAnActiveCommand() {
        val weak = FloatArray(512) { if (it % 2 == 0) 0.006f else -0.006f }
        val passive = FarFieldAudioEnhancer.enhance(weak, activeCommand = false)
        val active = FarFieldAudioEnhancer.enhance(weak, activeCommand = true)

        assertTrue(active.maxOf { kotlin.math.abs(it) } > passive.maxOf { kotlin.math.abs(it) })
        assertTrue(active.maxOf { kotlin.math.abs(it) } > 0.006f)
    }

    @Test
    fun nearSilenceIsNotAmplifiedIntoAFalseWake() {
        val silence = FloatArray(512) { 0.001f }
        assertSame(silence, FarFieldAudioEnhancer.enhance(silence, activeCommand = false))
    }

    @Test
    fun alreadyStrongSpeechIsLeftUntouched() {
        val strong = FloatArray(512) { if (it % 2 == 0) 0.08f else -0.08f }
        assertSame(strong, FarFieldAudioEnhancer.enhance(strong, activeCommand = true))
    }

    @Test
    fun whisperedSpeechIsRaisedEnoughForTheCommandDetector() {
        val whisper = FloatArray(512) { index -> if (index % 4 < 2) 0.0012f else -0.0012f }
        val enhanced = FarFieldAudioEnhancer.enhance(whisper, activeCommand = true)

        assertTrue(enhanced.maxOf { kotlin.math.abs(it) } >= 0.005f)
        assertTrue(enhanced.all { it in -0.98f..0.98f })
    }
}
