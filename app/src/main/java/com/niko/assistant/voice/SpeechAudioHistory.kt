package com.niko.assistant.voice

/** Audio-worker-only history aligned to the VAD sample clock; never written to disk. */
internal class SpeechAudioHistory(
    private val capacity: Int = 16_000 * 48,
    private val paddingBefore: Int = 16_000 * 180 / 1_000,
    private val paddingAfter: Int = 16_000 * 220 / 1_000,
) {
    private val buffer = FloatArray(capacity.also { require(it > 0) })
    private var total = 0L

    init { require(paddingBefore >= 0 && paddingAfter >= 0) }

    fun append(samples: FloatArray) {
        for (sample in samples) {
            buffer[(total % capacity).toInt()] = sample
            total++
        }
    }

    /** Sherpa's VAD must receive small frames, including when replaying a wake pre-roll. */
    fun feed(samples: FloatArray, accept: (FloatArray) -> Unit) {
        for (offset in samples.indices step 512) {
            val frame = if (samples.size <= 512) samples else samples.copyOfRange(offset, minOf(offset + 512, samples.size))
            append(frame)
            accept(frame)
        }
    }

    /** Keep the original segment intact and recover real audio around its boundaries. */
    fun withContext(start: Int, samples: FloatArray): FloatArray {
        if (samples.isEmpty() || start < 0) return samples
        val first = start.toLong()
        val end = first + samples.size
        val oldest = (total - capacity).coerceAtLeast(0L)
        if (first < oldest || end > total) return samples
        val from = maxOf(oldest, first - paddingBefore)
        val until = minOf(total, end + paddingAfter)
        val prefix = (first - from).toInt()
        val suffix = (until - end).toInt()
        if (prefix == 0 && suffix == 0) return samples
        return FloatArray(prefix + samples.size + suffix).also { output ->
            repeat(prefix) { output[it] = buffer[((from + it) % capacity).toInt()] }
            samples.copyInto(output, prefix)
            repeat(suffix) { output[prefix + samples.size + it] = buffer[((end + it) % capacity).toInt()] }
        }
    }

    /** Must accompany every VAD reset, including the pre-roll feed after a wake. */
    fun clear() { total = 0L }
}
