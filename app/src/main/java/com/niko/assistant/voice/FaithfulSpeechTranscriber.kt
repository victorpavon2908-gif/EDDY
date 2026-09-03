package com.niko.assistant.voice

/** At most two acoustic decodes. Never asks a language model to rewrite dictation. */
internal class FaithfulSpeechTranscriber(
    private val primaryDecoder: (FloatArray) -> String,
    private val alternateDecoder: ((FloatArray) -> String)? = null,
    private val denoiser: ((FloatArray) -> FloatArray?)? = null,
) {
    data class Result(val text: String, val needsClarification: Boolean = false)

    fun transcribe(samples: FloatArray): Result {
        if (samples.isEmpty()) return Result("")
        val primary = decode(primaryDecoder, samples)
        if (!TranscriptQuality.shouldRefine(primary, samples.size) &&
            !SpeechAudioFidelity.needsSecondPass(samples)
        ) return Result(primary)

        val alternate = alternateDecoder
        val refinement = if (alternate != null) {
            decode(alternate, samples)
        } else {
            val candidate = try { denoiser?.invoke(samples.copyOf()) } catch (_: Exception) { null }
            val cleaned = SpeechAudioFidelity.denoisedOrOriginal(samples, candidate)
            if (cleaned === samples) "" else decode(primaryDecoder, cleaned)
        }
        if (TranscriptQuality.requiresClarification(primary, refinement, samples.size)) {
            return Result("", needsClarification = true)
        }
        val selected = TranscriptQuality.choose(primary, refinement, samples.size)
        if (TranscriptQuality.shouldRefine(selected, samples.size)) return Result("")
        return Result(selected.takeIf(TranscriptQuality::isUsable).orEmpty())
    }

    private fun decode(decoder: (FloatArray) -> String, samples: FloatArray): String =
        try { decoder(samples).trim() } catch (_: Exception) { "" }
}
