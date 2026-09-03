package com.niko.assistant.voice

/** At most two acoustic decodes. Never asks a language model to rewrite dictation. */
internal class FaithfulSpeechTranscriber(
    private val primaryDecoder: (FloatArray) -> String,
    private val alternateDecoder: ((FloatArray) -> String)? = null,
    private val denoiser: ((FloatArray) -> FloatArray?)? = null,
    private val primaryName: String = "Canary",
    private val alternateName: String = "Whisper",
) {
    data class Result(
        val text: String,
        val needsClarification: Boolean = false,
        val engine: String = "",
        val latencyMs: Long = 0L,
    )

    fun transcribe(samples: FloatArray): Result {
        val started = System.nanoTime()
        if (samples.isEmpty()) return finish(Result("", engine = primaryName), started)
        val primary = decode(primaryDecoder, samples)
        if (!TranscriptQuality.shouldRefine(primary, samples.size) &&
            !SpeechAudioFidelity.needsSecondPass(samples)
        ) return finish(Result(primary, engine = primaryName), started)

        val alternate = alternateDecoder
        var refinementEngine = primaryName
        val refinement = if (alternate != null) {
            refinementEngine = alternateName
            decode(alternate, samples)
        } else {
            val candidate = try { denoiser?.invoke(samples.copyOf()) } catch (_: Exception) { null }
            val cleaned = SpeechAudioFidelity.denoisedOrOriginal(samples, candidate)
            if (cleaned === samples) "" else {
                refinementEngine = "$primaryName + GTCRN"
                decode(primaryDecoder, cleaned)
            }
        }
        if (TranscriptQuality.requiresClarification(primary, refinement, samples.size)) {
            val engine = if (refinementEngine.startsWith(primaryName)) refinementEngine else "$primaryName + $refinementEngine"
            return finish(Result("", needsClarification = true, engine = engine), started)
        }
        val selected = TranscriptQuality.choose(primary, refinement, samples.size)
        if (TranscriptQuality.shouldRefine(selected, samples.size)) return finish(Result("", engine = refinementEngine), started)
        val text = selected.takeIf(TranscriptQuality::isUsable).orEmpty()
        val engine = if (text.isNotBlank() && refinement.isNotBlank() && text == refinement && text != primary) refinementEngine else primaryName
        return finish(Result(text, engine = engine), started)
    }

    private fun finish(result: Result, startedNanos: Long): Result {
        val latency = ((System.nanoTime() - startedNanos) / 1_000_000L).coerceAtLeast(0L)
        val completed = result.copy(latencyMs = latency)
        LeoVoiceDiagnostics.recordTranscript(completed.text, completed.engine, completed.latencyMs, completed.needsClarification)
        return completed
    }

    private fun decode(decoder: (FloatArray) -> String, samples: FloatArray): String =
        try { decoder(samples).trim() } catch (_: Exception) { "" }
}
