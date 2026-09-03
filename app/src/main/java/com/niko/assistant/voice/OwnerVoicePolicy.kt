package com.niko.assistant.voice

import kotlin.math.sqrt

/** Voice preference only, not secure authentication or overlapping-speech separation. */
object OwnerVoicePolicy {
    const val ENROLLMENT_SAMPLES = 4
    const val MIN_SAMPLE_SECONDS = 2

    /** Normal same-speaker threshold. The old 0.62-for-every-window rule was too brittle. */
    const val ACCEPTANCE_THRESHOLD = 0.52f
    const val STRONG_ACCEPTANCE_THRESHOLD = 0.62f
    const val HARD_REJECT_THRESHOLD = 0.34f

    private const val ENROLLMENT_MEDIAN_THRESHOLD = 0.52f
    private const val ENROLLMENT_BEST_THRESHOLD = 0.58f
    private const val ENROLLMENT_HARD_REJECT = 0.38f

    data class MatchResult(
        val accepted: Boolean,
        val confidence: Float,
        val minimumScore: Float,
        val medianScore: Float,
        val scores: List<Float>,
    )

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

    /**
     * Robust speaker match for a real phone.
     *
     * The complete utterance is the strongest piece of evidence. Additional 1.5 s windows
     * protect against a speaker swap, but one moderately noisy/distant window no longer rejects
     * the owner by itself. A clearly different-speaker window still hard-rejects the turn.
     */
    fun evaluate(references: List<FloatArray>, segments: List<FloatArray>): MatchResult {
        val refs = references.filter(::valid)
        if (refs.isEmpty() || segments.isEmpty()) return MatchResult(false, 0f, 0f, 0f, emptyList())
        val dimension = refs.first().size
        if (refs.any { it.size != dimension } || segments.any { !valid(it) || it.size != dimension }) {
            return MatchResult(false, 0f, 0f, 0f, emptyList())
        }

        val scores = segments.map { segment -> refs.maxOf { reference -> similarity(reference, segment) } }
        val minimum = scores.minOrNull() ?: 0f
        val median = median(scores)
        val wholeTurn = scores.first()
        val hardMismatch = scores.any { it < HARD_REJECT_THRESHOLD }
        val majorityNeeded = scores.size / 2 + 1
        val majorityMatches = scores.count { it >= ACCEPTANCE_THRESHOLD }

        val accepted = when {
            hardMismatch -> false
            scores.size == 1 -> wholeTurn >= ACCEPTANCE_THRESHOLD
            wholeTurn >= STRONG_ACCEPTANCE_THRESHOLD && median >= 0.47f -> true
            else -> median >= ACCEPTANCE_THRESHOLD && majorityMatches >= majorityNeeded
        }

        val confidence = if (hardMismatch) minimum else {
            (median * 0.68f + wholeTurn * 0.32f).coerceIn(-1f, 1f)
        }
        return MatchResult(accepted, confidence, minimum, median, scores)
    }

    fun accepts(centroid: FloatArray, segments: List<FloatArray>): Boolean =
        evaluate(listOf(centroid), segments).accepted

    fun accepts(references: List<FloatArray>, segments: List<FloatArray>): Boolean =
        evaluate(references, segments).accepted

    /** Enrollment may contain normal variation in pace/distance, but never a clear speaker swap. */
    fun enrollmentConsistent(existing: List<FloatArray>, candidate: FloatArray): Boolean {
        if (!valid(candidate)) return false
        val refs = existing.filter { valid(it) && it.size == candidate.size }
        if (refs.isEmpty()) return true
        val scores = refs.map { similarity(it, candidate) }
        return scores.minOrNull()?.let { it >= ENROLLMENT_HARD_REJECT } == true &&
            median(scores) >= ENROLLMENT_MEDIAN_THRESHOLD &&
            (scores.maxOrNull() ?: 0f) >= ENROLLMENT_BEST_THRESHOLD
    }

    fun normalized(vector: FloatArray): FloatArray {
        if (!valid(vector)) return floatArrayOf()
        val norm = sqrt(vector.sumOf { it.toDouble() * it }).toFloat()
        return FloatArray(vector.size) { vector[it] / norm }
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
    }
}
