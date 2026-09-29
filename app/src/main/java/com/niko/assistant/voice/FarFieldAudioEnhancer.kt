package com.niko.assistant.voice

import kotlin.math.sqrt

/**
 * Nivelador digital conservador para voz débil.
 *
 * Trabaja antes de KWS/VAD/ASR. La ganancia ahora usa el ruido medido en el propio teléfono:
 * una voz suave claramente por encima del piso de ruido puede ganar nivel, mientras que ruido
 * distante o constante no se amplifica de forma ciega.
 */
object FarFieldAudioEnhancer {
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
            (mean + (samples[index] - mean) * gain).coerceIn(-0.98f, 0.98f)
        }
    }
}
