package com.niko.assistant.voice

import kotlin.math.sqrt

/**
 * Ganancia digital conservadora para voz lejana.
 *
 * Android sigue haciendo el primer trabajo con AGC/NoiseSuppressor. Esta capa solo
 * levanta bloques que ya contienen una señal útil; no amplifica silencio casi puro,
 * y usa menos ganancia mientras NIKO está en modo pasivo para evitar falsos despertares.
 */
object FarFieldAudioEnhancer {
    fun enhance(samples: FloatArray, activeCommand: Boolean): FloatArray {
        if (samples.isEmpty()) return samples

        var energy = 0.0
        for (sample in samples) energy += sample * sample
        val rms = sqrt(energy / samples.size).toFloat()

        val minimumUsefulRms = if (activeCommand) 0.0028f else 0.0042f
        val targetRms = if (activeCommand) 0.030f else 0.022f
        val maximumGain = if (activeCommand) 2.8f else 1.8f

        if (rms < minimumUsefulRms || rms >= targetRms) return samples
        val gain = (targetRms / rms).coerceIn(1f, maximumGain)
        if (gain <= 1.01f) return samples

        return FloatArray(samples.size) { index ->
            (samples[index] * gain).coerceIn(-0.98f, 0.98f)
        }
    }
}
