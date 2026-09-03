package com.niko.assistant.voice

import kotlin.math.abs
import kotlin.math.sqrt

/** Signal checks, not claims of speaker identity or recognition confidence. */
internal object SpeechAudioFidelity {
    fun needsSecondPass(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return false
        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
        val clipped = samples.count { abs(it) >= 0.98f }.toDouble() / samples.size
        return rms in 0.00045..0.008 || clipped >= 0.005
    }

    fun denoisedOrOriginal(original: FloatArray, candidate: FloatArray?): FloatArray {
        if (original.isEmpty() || candidate == null || candidate.size != original.size ||
            candidate.any { !it.isFinite() || abs(it) > 1f }
        ) return original
        val meanPower = original.sumOf { it.toDouble() * it } / original.size
        val usefulPower = maxOf(0.0004 * 0.0004, meanPower * 0.15)
        var usefulFrames = 0
        var erasedFrames = 0
        for (start in original.indices step 320) {
            val end = minOf(start + 320, original.size)
            var before = 0.0
            var after = 0.0
            for (i in start until end) {
                before += original[i].toDouble() * original[i]
                after += candidate[i].toDouble() * candidate[i]
            }
            if (before / (end - start) >= usefulPower) {
                usefulFrames++
                if (after < before * 0.04) erasedFrames++
            }
        }
        // A denoiser that erases >20% of signal-bearing frames can remove syllables.
        return if (usefulFrames == 0 || erasedFrames * 5 > usefulFrames) original else candidate
    }
}
