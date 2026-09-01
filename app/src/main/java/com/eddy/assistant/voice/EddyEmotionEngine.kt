package com.eddy.assistant.voice

import android.content.Context
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Señal afectiva local y liviana para EDDY.
 *
 * No intenta diagnosticar emociones humanas. Solo estima el tono acústico inmediato
 * (suave, calmado, enérgico o tenso) a partir de energía, cruces por cero y duración.
 * Todo se calcula y se guarda en el teléfono; no envía audio ni rasgos biométricos.
 */
class EddyEmotionEngine(context: Context) {
    enum class Tone(val label: String) {
        NEUTRAL("neutral"),
        SOFT("suave"),
        CALM("calmado"),
        ENERGETIC("enérgico"),
        TENSE("tenso"),
    }

    data class Snapshot(
        val tone: Tone,
        val confidence: Float,
        val timestamp: Long,
    )

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun observeSpeech(samples: FloatArray, sampleRate: Int): Snapshot {
        if (samples.isEmpty() || sampleRate <= 0) return Snapshot(Tone.NEUTRAL, 0f, System.currentTimeMillis())

        var energy = 0.0
        var peak = 0f
        var crossings = 0
        var previous = samples.first()
        for (sample in samples) {
            energy += sample * sample
            peak = maxOf(peak, abs(sample))
            if ((sample >= 0f) != (previous >= 0f)) crossings++
            previous = sample
        }

        val rms = sqrt(energy / samples.size).toFloat().coerceIn(0f, 1f)
        val zcr = crossings.toFloat() / samples.size.coerceAtLeast(1)
        val durationSec = samples.size.toFloat() / sampleRate

        val tone = when {
            rms >= 0.115f && zcr >= 0.09f -> Tone.TENSE
            rms >= 0.080f -> Tone.ENERGETIC
            rms <= 0.020f && durationSec >= 0.45f -> Tone.SOFT
            rms in 0.020f..0.060f && zcr < 0.10f -> Tone.CALM
            else -> Tone.NEUTRAL
        }

        val confidence = when (tone) {
            Tone.TENSE -> ((rms - 0.09f) * 5f + (zcr - 0.07f) * 2f).coerceIn(0.35f, 0.92f)
            Tone.ENERGETIC -> ((rms - 0.05f) * 6f).coerceIn(0.35f, 0.88f)
            Tone.SOFT -> ((0.035f - rms) * 12f).coerceIn(0.30f, 0.82f)
            Tone.CALM -> (0.72f - abs(rms - 0.04f) * 7f).coerceIn(0.35f, 0.82f)
            Tone.NEUTRAL -> 0.35f
        }

        val now = System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_TONE, tone.name)
            .putFloat(KEY_CONFIDENCE, confidence)
            .putLong(KEY_TIMESTAMP, now)
            .putFloat(KEY_RMS, rms)
            .putFloat(KEY_PEAK, peak)
            .apply()
        return Snapshot(tone, confidence, now)
    }

    companion object {
        private const val PREFS = "eddy_affect_local"
        private const val KEY_TONE = "tone"
        private const val KEY_CONFIDENCE = "confidence"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_RMS = "rms"
        private const val KEY_PEAK = "peak"
        private const val FRESH_MS = 120_000L

        fun latest(context: Context): Snapshot? {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
            if (timestamp <= 0L || System.currentTimeMillis() - timestamp > FRESH_MS) return null
            val tone = runCatching { Tone.valueOf(prefs.getString(KEY_TONE, Tone.NEUTRAL.name).orEmpty()) }
                .getOrDefault(Tone.NEUTRAL)
            return Snapshot(tone, prefs.getFloat(KEY_CONFIDENCE, 0f), timestamp)
        }
    }
}
