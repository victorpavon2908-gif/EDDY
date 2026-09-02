package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class NearFieldAudioFocusTest {
    @Test fun backgroundIsAttenuatedInsteadOfAmplified() {
        val focus = NearFieldAudioFocus()
        val background = FloatArray(16000) { if (it % 2 == 0) 0.002f else -0.002f }
        val output = focus.process(background)
        assertTrue(output.indices.all { abs(output[it]) <= abs(background[it]) })
        assertTrue(abs(output.last()) < 0.001f)
    }
    @Test fun nearSpeechRecoversRapidlyWithoutClipping() {
        val focus = NearFieldAudioFocus()
        focus.process(FloatArray(16000) { 0.001f })
        val near = focus.process(FloatArray(512) { 0.1f })
        assertTrue(near[100] > 0.099f)
        assertTrue(near.all { it in 0f..0.1f })
    }
    @Test fun quietWordEndingsAreNotHardMuted() {
        val focus = NearFieldAudioFocus()
        focus.process(FloatArray(512) { 0.1f })
        val ending = focus.process(FloatArray(512) { 0.003f })
        assertTrue(ending.first() > 0.0029f)
        assertTrue(ending.all { it > 0f })
    }
}
