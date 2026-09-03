package com.niko.assistant.voice

/**
 * Respaldo local para el hotword corto "Leo".
 * Sherpa KWS sigue siendo la primera ruta; Silero + Canary rescatan pronunciaciones que el
 * transducer pierde. Los límites se leen del perfil de afinación medido en el teléfono.
 */
object LeoPassiveWakeVerifier {
    const val MIN_SPEECH_SAMPLES = 16_000 * 3 / 20 // baseline: 150 ms
    const val MAX_SPEECH_SAMPLES = 16_000 * 16 / 5 // baseline: 3.2 s
    const val PROBE_COOLDOWN_MS = 650L

    fun shouldProbe(sampleCount: Int, nowMs: Long, lastProbeAtMs: Long): Boolean {
        val tuning = LeoVoiceTuning.current()
        val minimum = 16_000 * tuning.minPassiveSpeechMs / 1_000
        val maximum = 16_000 * tuning.maxPassiveSpeechMs / 1_000
        return sampleCount in minimum..maximum &&
            (lastProbeAtMs <= 0L || nowMs - lastProbeAtMs >= tuning.passiveProbeCooldownMs)
    }

    fun consumeTranscript(transcript: String, nowMs: Long = System.currentTimeMillis()): WakeResult =
        WakeWordGate().consume(transcript, nowMs)
}
