package com.niko.assistant.voice

import kotlin.math.sqrt

/**
 * Nivelador digital conservador para voz débil.
 *
 * Trabaja antes de KWS/VAD/ASR. Calcula energía sin componente DC para no convertir
 * el ruido constante del micrófono en una falsa voz y aplica más ganancia cuando ya
 * existe una ventana de comando. En modo pasivo sigue siendo prudente: el wake word
 * y la verificación de hablante continúan decidiendo si LEO debe activarse.
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

        val minimumUsefulRms = if (activeCommand) 0.00055f else 0.00090f
        val targetRms = if (activeCommand) 0.028f else 0.018f
        val maximumGain = if (activeCommand) 4.5f else 2.4f

        if (rms < minimumUsefulRms || rms >= targetRms) return samples
        val gain = (targetRms / rms).coerceIn(1f, maximumGain)
        if (gain <= 1.01f) return samples

        return FloatArray(samples.size) { index ->
            (mean + (samples[index] - mean) * gain).coerceIn(-0.98f, 0.98f)
        }
    }
}
