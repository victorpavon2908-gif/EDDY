package com.niko.assistant.voice

import kotlin.math.sqrt

/** Voice preference only, not secure authentication or overlapping-speech separation. */
object OwnerVoicePolicy {
    const val ENROLLMENT_SAMPLES = 4
    const val MIN_SAMPLE_SECONDS = 2

    fun valid(vector: FloatArray): Boolean = vector.isNotEmpty() && vector.all { it.isFinite() } &&
        vector.any { kotlin.math.abs(it) > 0.000001f }

    fun similarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || !valid(a) || !valid(b)) return 0f
        var dot = 0.0
        var aa = 0.0
        var bb = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i]
            aa += a[i].toDouble() * a[i]
            bb += b[i].toDouble() * b[i]
        }
        return (dot / sqrt(aa * bb)).toFloat().coerceIn(-1f, 1f)
    }

    fun accepts(centroid: FloatArray, segments: List<FloatArray>): Boolean =
        segments.isNotEmpty() && segments.all { similarity(centroid, it) >= 0.62f }

    fun normalized(vector: FloatArray): FloatArray {
        if (!valid(vector)) return floatArrayOf()
        val norm = sqrt(vector.sumOf { it.toDouble() * it }).toFloat()
        return FloatArray(vector.size) { vector[it] / norm }
    }
}
