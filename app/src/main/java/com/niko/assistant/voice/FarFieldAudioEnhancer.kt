package com.niko.assistant.voice

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Nivelador digital DSP para voz débil y campo lejano (far-field).
 *
 * Trabaja en tiempo real sobre bloques cortos (512 muestras / 32 ms) antes de KWS/VAD/ASR:
 * 1. Centrado DC sin discontinuidades de fase entre bloques consecutivos del KeywordSpotter.
 * 2. Control automático de ganancia (AGC) acotado por el piso de ruido real medido en el teléfono.
 * 3. Limitador de rodilla suave (soft-knee tanh) sobre 0.82 para evitar recorte duro que distorsiona ASR/KWS.
 */
object FarFieldAudioEnhancer {
    private const val HPF_ALPHA = 0.969f
    private const val SOFT_KNEE = 0.82f
    private const val CEILING = 0.98f

    fun enhance(samples: FloatArray, activeCommand: Boolean): FloatArray {
        if (samples.isEmpty()) return samples

        var sum = 0.0
        for (sample in samples) sum += sample
        val mean = (sum / samples.size).toFloat()
        var centeredEnergy = 0.0
        for (sample in samples) {
            val centered = sample - mean
            centeredEnergy += centered * centered
        }
        val rms = sqrt(centeredEnergy / samples.size).toFloat()
        LeoVoiceDiagnostics.observeAudio(samples, activeCommand)

        val tuning = LeoVoiceTuning.current()
        val minimumUsefulRms = if (activeCommand) tuning.activeMinimumUsefulRms else tuning.passiveMinimumUsefulRms
        val targetRms = if (activeCommand) tuning.activeTargetRms else tuning.passiveTargetRms
        val maximumGain = if (activeCommand) tuning.activeMaxGain else tuning.passiveMaxGain
        val noiseGuard = LeoVoiceDiagnostics.currentNoiseRms() * if (activeCommand) 0.85f else 1.50f
        val usableFloor = maxOf(minimumUsefulRms, noiseGuard)

        if (rms < usableFloor || rms >= targetRms) return samples
        val gain = (targetRms / rms).coerceIn(1f, maximumGain)
        if (gain <= 1.01f) return samples

        return FloatArray(samples.size) { index ->
            softLimit(mean + (samples[index] - mean) * gain)
        }
    }

    /** First-order high-pass filter (~80 Hz at 16 kHz) for full utterance buffers (not stateless 32ms KWS chunks). */
    internal fun highPassFilter(samples: FloatArray, alpha: Float = HPF_ALPHA): FloatArray {
        if (samples.isEmpty()) return samples
        val out = FloatArray(samples.size)
        var prevIn = samples[0]
        var prevOut = 0f
        out[0] = 0f
        for (i in 1 until samples.size) {
            val x = samples[i]
            val y = alpha * (prevOut + x - prevIn)
            out[i] = y
            prevIn = x
            prevOut = y
        }
        return out
    }

    /** Linear up to [SOFT_KNEE], then smoothly compressed via tanh into [-0.98, 0.98] without hard-clip harmonics. */
    internal fun softLimit(value: Float): Float {
        val magnitude = abs(value)
        if (magnitude <= SOFT_KNEE) return value
        val headroom = CEILING - SOFT_KNEE
        val excess = (magnitude - SOFT_KNEE) / headroom
        val compressed = SOFT_KNEE + headroom * tanh(excess.toDouble()).toFloat()
        return (sign(value) * compressed).coerceIn(-CEILING, CEILING)
    }
}
