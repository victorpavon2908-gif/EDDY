package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechAudioFidelityTest {
    private val original = FloatArray(1_600) { if (it % 2 == 0) 0.004f else -0.004f }

    @Test fun rejectsChangedDurationSilenceAndNonFiniteSamples() {
        assertSame(original, SpeechAudioFidelity.denoisedOrOriginal(original, FloatArray(1_599)))
        assertSame(original, SpeechAudioFidelity.denoisedOrOriginal(original, FloatArray(1_600)))
        assertSame(original, SpeechAudioFidelity.denoisedOrOriginal(original, FloatArray(1_600) { Float.NaN }))
    }

    @Test fun rejectsCleanupThatErasesSeveralSyllableSizedFrames() {
        val damaged = original.copyOf().also { it.fill(0f, 0, 640) }
        assertSame(original, SpeechAudioFidelity.denoisedOrOriginal(original, damaged))
    }

    @Test fun acceptsModerateCleanupThatPreservesSignalBearingFrames() {
        val cleaned = FloatArray(original.size) { original[it] * 0.8f }
        assertSame(cleaned, SpeechAudioFidelity.denoisedOrOriginal(original, cleaned))
    }

    @Test fun quietAndClippedAudioGetCheckedButNormalSpeechStaysFast() {
        assertTrue(SpeechAudioFidelity.needsSecondPass(original))
        assertTrue(SpeechAudioFidelity.needsSecondPass(FloatArray(1_600) { 0.99f }))
        assertFalse(SpeechAudioFidelity.needsSecondPass(FloatArray(1_600) { 0.04f }))
        assertFalse(SpeechAudioFidelity.needsSecondPass(FloatArray(1_600)))
    }
}
