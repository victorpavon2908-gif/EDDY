package com.niko.assistant.voice

/** Back off across short-lived captures; a healthy minute earns a fresh retry budget. */
class VoiceRecoveryPolicy {
    private var failures = 0
    private var healthySince: Long? = null
    private val repairedModels = mutableSetOf<String>()

    fun started(now: Long) { healthySince = now }

    fun nextDelayMillis(now: Long): Long {
        if (healthySince?.let { now - it >= 60_000L } == true) failures = 0
        healthySince = null
        val delays = longArrayOf(5_000L, 15_000L, 60_000L, 300_000L)
        return delays[failures.coerceAtMost(delays.lastIndex)].also {
            failures = (failures + 1).coerceAtMost(delays.size)
        }
    }

    /** Never repeatedly delete/download the same model because native initialization failed. */
    fun allowModelRepair(modelId: String): Boolean = repairedModels.add(modelId)
}
