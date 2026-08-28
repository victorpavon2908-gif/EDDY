package com.eddy.assistant.localai

import android.content.Context
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Huella vocal local adaptativa. Nunca guarda audio: solo un vector numérico.
 * No debe usarse como autenticación bancaria/biométrica de seguridad.
 */
class EddyVoiceProfile(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("eddy_voice_profile_v1", Context.MODE_PRIVATE)

    val sampleCount: Int get() = prefs.getInt(KEY_COUNT, 0)
    val isEnrolled: Boolean get() = sampleCount >= MIN_ENROLLMENT_SAMPLES && loadCentroid() != null

    fun score(embedding: FloatArray): Float {
        val centroid = loadCentroid() ?: return if (sampleCount == 0) 1f else 0f
        return cosine(centroid, embedding)
    }

    /**
     * Durante el arranque aprende de las primeras activaciones explícitas con EDDY.
     * Una vez formado el perfil, solo se adapta a muestras que ya se parecen al dueño.
     */
    fun acceptAndLearn(embedding: FloatArray, threshold: Float = 0.58f): VoiceDecision {
        if (embedding.isEmpty()) return VoiceDecision(false, 0f, isEnrolled)
        val current = loadCentroid()
        if (current == null || sampleCount < MIN_ENROLLMENT_SAMPLES) {
            blend(embedding, force = true)
            return VoiceDecision(true, 1f, isEnrolled)
        }

        val similarity = cosine(current, embedding)
        val accepted = similarity >= threshold
        if (accepted && similarity >= threshold + 0.04f) blend(embedding, force = false)
        return VoiceDecision(accepted, similarity, true)
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    private fun blend(sample: FloatArray, force: Boolean) {
        val current = loadCentroid()
        val count = sampleCount
        val alpha = when {
            current == null -> 1f
            force && count < MIN_ENROLLMENT_SAMPLES -> 1f / (count + 1).toFloat()
            else -> 0.06f
        }
        val next = if (current == null || current.size != sample.size) {
            normalize(sample.copyOf())
        } else {
            normalize(FloatArray(sample.size) { index ->
                current[index] * (1f - alpha) + sample[index] * alpha
            })
        }
        prefs.edit()
            .putString(KEY_VECTOR, encode(next))
            .putInt(KEY_COUNT, (count + 1).coerceAtMost(10_000))
            .apply()
    }

    private fun loadCentroid(): FloatArray? = prefs.getString(KEY_VECTOR, null)?.let(::decode)

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dot = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            aa += a[i] * a[i]
            bb += b[i] * b[i]
        }
        if (aa <= 0.0 || bb <= 0.0) return 0f
        return (dot / sqrt(aa * bb)).toFloat().coerceIn(-1f, 1f)
    }

    private fun normalize(values: FloatArray): FloatArray {
        val norm = sqrt(values.sumOf { (it * it).toDouble() }).toFloat()
        if (norm <= 1e-7f) return values
        for (i in values.indices) values[i] /= norm
        return values
    }

    private fun encode(values: FloatArray): String {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
    }

    private fun decode(raw: String): FloatArray? = runCatching {
        val bytes = Base64.decode(raw, Base64.NO_WRAP)
        if (bytes.isEmpty() || bytes.size % 4 != 0) return@runCatching null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        FloatArray(bytes.size / 4) { buffer.float }
    }.getOrNull()

    data class VoiceDecision(
        val accepted: Boolean,
        val similarity: Float,
        val enrolled: Boolean,
    )

    companion object {
        private const val KEY_VECTOR = "owner_centroid"
        private const val KEY_COUNT = "owner_samples"
        private const val MIN_ENROLLMENT_SAMPLES = 4
    }
}
