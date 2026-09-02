package com.niko.assistant.voice

/**
 * Respaldo local para el hotword corto "Leo".
 * Sherpa KWS sigue siendo la primera ruta; Silero + Canary rescatan pronunciaciones que el
 * transducer pierde, con una ventana más corta para que el usuario no tenga que repetir el nombre.
 */
object LeoPassiveWakeVerifier {
    const val MIN_SPEECH_SAMPLES = 16_000 * 3 / 20 // 150 ms
    const val MAX_SPEECH_SAMPLES = 16_000 * 16 / 5 // 3.2 s
    const val PROBE_COOLDOWN_MS = 650L

    fun shouldProbe(sampleCount: Int, nowMs: Long, lastProbeAtMs: Long): Boolean =
        sampleCount in MIN_SPEECH_SAMPLES..MAX_SPEECH_SAMPLES &&
            (lastProbeAtMs <= 0L || nowMs - lastProbeAtMs >= PROBE_COOLDOWN_MS)

    fun consumeTranscript(transcript: String, nowMs: Long = System.currentTimeMillis()): WakeResult =
        WakeWordGate().consume(transcript, nowMs)
}
