package com.niko.assistant.voice

import kotlin.math.sqrt

/** Smooth expansion attenuates quiet background frames without boosting far-away voices.
 * Loudness is not identity: speaker verification is handled separately.
 */
class NearFieldAudioFocus {
    private var gain = 1f
    fun process(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size).toFloat()
        val target = (rms / 0.009f).coerceIn(0.12f, 1f)
        val output = FloatArray(samples.size)
        for (i in samples.indices) {
            // Fast attack (~1 ms), slow release (~50 ms at 16 kHz) preserves word endings.
            gain += (target - gain) * if (target > gain) 0.08f else 0.00125f
            output[i] = samples[i] * gain
        }
        return output
    }
    fun reset() { gain = 1f }
}
