package com.niko.assistant.voice

/**
 * Respaldo local para un hotword deliberadamente corto como "Leo".
 *
 * El KWS de Sherpa sigue siendo la primera ruta de bajo consumo. Cuando una ráfaga breve
 * de voz termina sin que el KWS haya disparado, Silero VAD puede entregar ese segmento a
 * Canary ES para verificar únicamente si la transcripción EMPIEZA llamando a LEO.
 * Nada se envía a Internet y las transcripciones que no son wake se descartan.
 */
object LeoPassiveWakeVerifier {
    const val MIN_SPEECH_SAMPLES = 16_000 / 5 // 200 ms
    const val MAX_SPEECH_SAMPLES = 16_000 * 5 / 2 // 2.5 s
    const val PROBE_COOLDOWN_MS = 1_000L

    fun shouldProbe(sampleCount: Int, nowMs: Long, lastProbeAtMs: Long): Boolean =
        sampleCount in MIN_SPEECH_SAMPLES..MAX_SPEECH_SAMPLES &&
            (lastProbeAtMs <= 0L || nowMs - lastProbeAtMs >= PROBE_COOLDOWN_MS)

    fun consumeTranscript(transcript: String, nowMs: Long = System.currentTimeMillis()): WakeResult =
        WakeWordGate().consume(transcript, nowMs)
}
