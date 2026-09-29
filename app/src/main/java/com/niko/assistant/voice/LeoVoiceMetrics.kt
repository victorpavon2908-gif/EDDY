package com.niko.assistant.voice

import kotlin.math.roundToInt

enum class LeoVoiceScenario(val label: String) {
    NORMAL("Voz normal"),
    FAST("Voz rápida"),
    SLOW("Voz lenta"),
    QUIET("Voz baja"),
    WHISPER("Susurro"),
    DISTANCE_1M("A 1 metro"),
    DISTANCE_2M("A 2 metros"),
    FAN("Con ventilador"),
    TV("Con TV"),
    HEADPHONES("Con auriculares"),
    LOCKED_SCREEN("Pantalla bloqueada"),
}

data class LeoWakeMetrics(
    val truePositives: Int = 0,
    val falsePositives: Int = 0,
    val falseNegatives: Int = 0,
    val unlabeledWakes: Int = 0,
    val targetCalls: Int = 100,
    val completedCalls: Int = 0,
    val falsePositiveWatchMs: Long = 0L,
    val quietTruePositives: Int = 0,
    val quietFalseNegatives: Int = 0,
) {
    val recall: Double
        get() = truePositives.toDouble() / (truePositives + falseNegatives).coerceAtLeast(1)
    val precision: Double
        get() = truePositives.toDouble() / (truePositives + falsePositives).coerceAtLeast(1)
    val quietRecall: Double
        get() = quietTruePositives.toDouble() / (quietTruePositives + quietFalseNegatives).coerceAtLeast(1)
    val falsePositivesPerHour: Double
        get() = if (falsePositiveWatchMs <= 0L) 0.0 else falsePositives * 3_600_000.0 / falsePositiveWatchMs

    fun recallPercent(): Int = (recall * 100.0).roundToInt()
}

data class LeoVoiceTuningProfile(
    val keywordScore: Float = 1.50f,
    val keywordThreshold: Float = 0.11f,
    val trailingBlanks: Int = 3,
    val minPassiveSpeechMs: Int = 150,
    val maxPassiveSpeechMs: Int = 3_200,
    val passiveProbeCooldownMs: Long = 650L,
    val passiveMinimumUsefulRms: Float = 0.00090f,
    val activeMinimumUsefulRms: Float = 0.00055f,
    val passiveTargetRms: Float = 0.018f,
    val activeTargetRms: Float = 0.028f,
    val passiveMaxGain: Float = 2.4f,
    val activeMaxGain: Float = 4.5f,
    val preRollMs: Int = 2_000,
) {
    fun sanitized(): LeoVoiceTuningProfile = copy(
        keywordScore = keywordScore.coerceIn(1.25f, 1.80f),
        keywordThreshold = keywordThreshold.coerceIn(0.07f, 0.18f),
        trailingBlanks = trailingBlanks.coerceIn(2, 6),
        minPassiveSpeechMs = minPassiveSpeechMs.coerceIn(100, 320),
        maxPassiveSpeechMs = maxPassiveSpeechMs.coerceIn(2_400, 4_000),
        passiveProbeCooldownMs = passiveProbeCooldownMs.coerceIn(450L, 1_000L),
        passiveMinimumUsefulRms = passiveMinimumUsefulRms.coerceIn(0.00055f, 0.00140f),
        activeMinimumUsefulRms = activeMinimumUsefulRms.coerceIn(0.00035f, 0.00100f),
        passiveTargetRms = passiveTargetRms.coerceIn(0.014f, 0.024f),
        activeTargetRms = activeTargetRms.coerceIn(0.022f, 0.036f),
        passiveMaxGain = passiveMaxGain.coerceIn(1.8f, 3.2f),
        activeMaxGain = activeMaxGain.coerceIn(3.0f, 5.5f),
        preRollMs = preRollMs.coerceIn(1_200, 2_000),
    )
}

/** Pure recommendation logic: tuning is based on labeled phone measurements, never guesses. */
object LeoVoiceTuningAdvisor {
    fun recommend(
        current: LeoVoiceTuningProfile,
        metrics: LeoWakeMetrics,
        averageSnrDb: Float,
    ): LeoVoiceTuningProfile {
        if (metrics.completedCalls < 20) return current
        var next = current
        val enoughFpObservation = metrics.falsePositiveWatchMs >= 10 * 60_000L
        val fpPerHour = metrics.falsePositivesPerHour

        if (metrics.recall < 0.95 && (!enoughFpObservation || fpPerHour <= 1.0)) {
            next = next.copy(
                keywordScore = next.keywordScore - 0.04f,
                keywordThreshold = next.keywordThreshold - 0.008f,
                minPassiveSpeechMs = next.minPassiveSpeechMs - 20,
                passiveProbeCooldownMs = next.passiveProbeCooldownMs - 60L,
                preRollMs = next.preRollMs + 120,
            )
        }
        if (enoughFpObservation && fpPerHour > 1.0) {
            next = next.copy(
                keywordScore = next.keywordScore + 0.05f,
                keywordThreshold = next.keywordThreshold + 0.010f,
                minPassiveSpeechMs = next.minPassiveSpeechMs + 20,
                trailingBlanks = next.trailingBlanks + 1,
                passiveMaxGain = next.passiveMaxGain - 0.15f,
            )
        }

        val quietSamples = metrics.quietTruePositives + metrics.quietFalseNegatives
        if (quietSamples >= 8 && metrics.quietRecall < 0.90 && averageSnrDb >= 4f && (!enoughFpObservation || fpPerHour <= 1.0)) {
            next = next.copy(
                activeMinimumUsefulRms = next.activeMinimumUsefulRms * 0.90f,
                passiveMinimumUsefulRms = next.passiveMinimumUsefulRms * 0.92f,
                activeMaxGain = next.activeMaxGain + 0.30f,
                passiveMaxGain = next.passiveMaxGain + 0.20f,
            )
        }
        return next.sanitized()
    }
}
