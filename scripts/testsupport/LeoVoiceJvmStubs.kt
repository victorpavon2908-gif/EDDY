package com.niko.assistant.voice

/** JVM-only hooks used by scripts/test_voice_fidelity.sh; Android owns the real implementations. */
object LeoVoiceTuning {
    fun current(): LeoVoiceTuningProfile = LeoVoiceTuningProfile()
}

object LeoVoiceDiagnostics {
    fun observeAudio(samples: FloatArray, activeCommand: Boolean) = Unit
    fun currentNoiseRms(): Float = 0.0012f
    fun recordTranscript(text: String, engine: String, latencyMs: Long, clarification: Boolean) = Unit
}
