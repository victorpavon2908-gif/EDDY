package com.niko.assistant.voice

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detector acústico ligero de palabra de activación ("LEO" / "Oye LEO") sobre PCM 16 kHz.
 *
 * Opera con extracción de envolvente RMS por ventanas de 20 ms, tasa de cruces por cero (ZCR),
 * razón de energía en banda vocal (300-3400 Hz), cadencia silábica bisílaba ("Le-o") y un
 * perceptrón cuantizado de ultrabajo consumo para prefiltrar audio antes del decodificador ASR.
 */
object LeoAcousticWakeScorer {
    data class WakeCandidate(
        val detected: Boolean,
        val confidence: Float,
        val activeDurationMs: Int,
        val syllablePeaks: Int,
        val vocalBandRatio: Float,
        val reason: String,
    )

    private const val FRAME_MS = 20
    private const val MIN_WAKE_DURATION_MS = 180
    private const val MAX_WAKE_DURATION_MS = 1_650
    private const val DEFAULT_THRESHOLD = 0.62f

    fun score(
        samples: ShortArray,
        sampleRate: Int = 16_000,
        threshold: Float = DEFAULT_THRESHOLD,
    ): WakeCandidate {
        if (samples.isEmpty() || sampleRate < 8_000) {
            return WakeCandidate(false, 0f, 0, 0, 0f, "empty_audio")
        }
        val frameSize = max(1, (sampleRate * FRAME_MS) / 1000)
        val frameCount = samples.size / frameSize
        if (frameCount < 6) {
            return WakeCandidate(false, 0f, frameCount * FRAME_MS, 0, 0f, "too_short")
        }

        val rmsEnvelope = FloatArray(frameCount)
        val zcrEnvelope = FloatArray(frameCount)
        var totalEnergy = 0.0
        var vocalBandEnergy = 0.0
        var prevSample = 0f
        var bandState = 0f

        for (f in 0 until frameCount) {
            val offset = f * frameSize
            var sumSq = 0.0
            var zeroCrossings = 0
            for (i in 0 until frameSize) {
                val x = samples[offset + i] / 32768f
                sumSq += x * x
                if ((x >= 0f && prevSample < 0f) || (x < 0f && prevSample >= 0f)) {
                    zeroCrossings++
                }
                // Filtro paso-banda de primer orden aproximado para banda formántica vocal (300-3400 Hz)
                val diff = x - prevSample
                bandState = 0.78f * bandState + 0.22f * diff
                vocalBandEnergy += bandState * bandState
                totalEnergy += x * x
                prevSample = x
            }
            rmsEnvelope[f] = sqrt(sumSq / frameSize).toFloat()
            zcrEnvelope[f] = zeroCrossings.toFloat() / frameSize
        }

        val maxRms = rmsEnvelope.maxOrNull() ?: 0f
        val noiseFloor = rmsEnvelope.sortedArray().let { sorted ->
            sorted[min(sorted.size - 1, max(0, sorted.size / 5))]
        }
        if (maxRms < 0.025f || maxRms < noiseFloor * 1.8f) {
            return WakeCandidate(false, 0.05f, 0, 0, 0f, "silence_or_low_snr")
        }

        val activeThreshold = max(0.02f, noiseFloor * 1.6f + (maxRms - noiseFloor) * 0.22f)
        var firstActive = -1
        var lastActive = -1
        var activeFrames = 0
        var meanActiveZcr = 0f
        for (f in 0 until frameCount) {
            if (rmsEnvelope[f] >= activeThreshold) {
                if (firstActive == -1) firstActive = f
                lastActive = f
                activeFrames++
                meanActiveZcr += zcrEnvelope[f]
            }
        }
        if (activeFrames == 0 || firstActive == -1) {
            return WakeCandidate(false, 0.05f, 0, 0, 0f, "no_active_speech")
        }
        meanActiveZcr /= activeFrames
        val spanMs = (lastActive - firstActive + 1) * FRAME_MS
        if (spanMs < MIN_WAKE_DURATION_MS) {
            return WakeCandidate(false, 0.12f, spanMs, 1, 0f, "transient_click")
        }
        if (spanMs > MAX_WAKE_DURATION_MS) {
            return WakeCandidate(false, 0.18f, spanMs, 0, 0f, "continuous_speech")
        }

        // Ruido blanco o fricativa pura presenta ZCR excesivo (> 0.36)
        if (meanActiveZcr > 0.36f) {
            return WakeCandidate(false, 0.10f, spanMs, 0, 0f, "high_frequency_noise")
        }

        // Conteo de picos silábicos suavizados ("Le-o" u "O-ye Le-o": 1 a 4 picos vocálicos)
        val smoothed = FloatArray(frameCount)
        for (f in 0 until frameCount) {
            val left = rmsEnvelope[max(0, f - 1)]
            val center = rmsEnvelope[f]
            val right = rmsEnvelope[min(frameCount - 1, f + 1)]
            smoothed[f] = (left + 2f * center + right) / 4f
        }
        var peaks = 0
        var lastPeakFrame = -10
        val peakMin = max(activeThreshold * 1.15f, maxRms * 0.42f)
        for (f in max(1, firstActive) until min(frameCount - 1, lastActive + 1)) {
            if (smoothed[f] >= peakMin &&
                smoothed[f] >= smoothed[f - 1] &&
                smoothed[f] >= smoothed[f + 1] &&
                f - lastPeakFrame >= 4 // >= 80 ms entre sílabas
            ) {
                peaks++
                lastPeakFrame = f
            }
        }
        if (peaks == 0 && activeFrames >= 6) peaks = 1

        val vocalRatio = if (totalEnergy > 1e-9) {
            (vocalBandEnergy / totalEnergy).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }

        // Perceptrón local sobre rasgos normalizados:
        // f1: duración compatible con "Leo" / "Oye Leo" (220..950 ms óptimo)
        val durationScore = when {
            spanMs in 220..950 -> 1.0f
            spanMs in 180..1350 -> 0.72f
            else -> 0.25f
        }
        // f2: cadencia silábica (1..4 picos)
        val syllableScore = when (peaks) {
            2, 3 -> 1.0f
            1, 4 -> 0.78f
            else -> 0.25f
        }
        // f3: ZCR propio de vocales sonoras ("e", "o") en 16 kHz (0.03 .. 0.22)
        val zcrScore = if (meanActiveZcr in 0.025f..0.24f) 1.0f else 0.30f
        // f4: SNR relativo
        val snrScore = ((maxRms - noiseFloor) / max(0.04f, maxRms)).coerceIn(0f, 1f)
        // f5: Energía en banda vocal
        val bandScore = if (vocalRatio in 0.04f..0.68f) 1.0f else 0.40f

        val logit = -2.15f +
            1.35f * durationScore +
            1.25f * syllableScore +
            0.95f * zcrScore +
            0.85f * snrScore +
            0.65f * bandScore
        val probability = (1.0f / (1.0f + exp(-logit))).coerceIn(0f, 1f)
        val detected = probability >= threshold
        return WakeCandidate(
            detected = detected,
            confidence = probability,
            activeDurationMs = spanMs,
            syllablePeaks = peaks,
            vocalBandRatio = vocalRatio,
            reason = if (detected) "acoustic_wake_match" else "below_threshold",
        )
    }

    fun scoreWithTranscript(
        samples: ShortArray,
        transcript: String?,
        sampleRate: Int = 16_000,
    ): WakeCandidate {
        val acoustic = score(samples, sampleRate)
        if (transcript.isNullOrBlank()) return acoustic
        val clean = Normalizer.normalize(transcript.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val hasExplicitWake = Regex("^(?:oye |hola |hey )?(?:leo|niko|nico)(?:\\b|$)").containsMatchIn(clean)
        if (!hasExplicitWake) return acoustic
        val boosted = (acoustic.confidence * 0.45f + 0.55f).coerceIn(0f, 1f)
        return acoustic.copy(
            detected = boosted >= DEFAULT_THRESHOLD,
            confidence = boosted,
            reason = "hybrid_acoustic_lexical_wake",
        )
    }
}
