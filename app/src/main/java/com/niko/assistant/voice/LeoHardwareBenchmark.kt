package com.niko.assistant.voice

import java.util.Locale

/**
 * Evaluador ejecutable del protocolo de validación física en dispositivo real
 * (`PROTOCOLO_VALIDACION_TELEFONO.md`) y autocalibrador determinista de `LeoVoiceTuningProfile`.
 */
object LeoHardwareBenchmark {
    data class BenchmarkMetrics(
        val wakeLatencyP95Ms: Long,
        val falseWakesPerHour: Double,
        val bargeInStopLatencyMs: Long,
        val uiFrameP95Ms: Double,
        val batteryDrainPercentPerHour: Double,
        val echoRejectionDb: Double,
    )

    data class CriterionResult(
        val id: String,
        val label: String,
        val measured: String,
        val target: String,
        val passed: Boolean,
    )

    data class BenchmarkReport(
        val passedAll: Boolean,
        val scorePercent: Int,
        val criteria: List<CriterionResult>,
        val recommendedTuning: LeoVoiceTuningProfile,
        val summary: String,
    )

    const val TARGET_WAKE_P95_MS = 700L
    const val TARGET_FALSE_WAKES_PER_HOUR = 0.0
    const val TARGET_BARGE_IN_STOP_MS = 250L
    const val TARGET_UI_FRAME_P95_MS = 16.67
    const val TARGET_BATTERY_DRAIN_PCT_PER_HOUR = 6.0
    const val TARGET_ECHO_REJECTION_DB = 18.0

    fun evaluate(
        metrics: BenchmarkMetrics,
        currentTuning: LeoVoiceTuningProfile = LeoVoiceTuningProfile(),
    ): BenchmarkReport {
        val criteria = listOf(
            CriterionResult(
                id = "wake_latency_p95",
                label = "Latencia de activación por voz (p95)",
                measured = "${metrics.wakeLatencyP95Ms} ms",
                target = "<= $TARGET_WAKE_P95_MS ms",
                passed = metrics.wakeLatencyP95Ms in 1..TARGET_WAKE_P95_MS,
            ),
            CriterionResult(
                id = "false_wakes",
                label = "Falsos positivos de wake-word",
                measured = String.format(Locale.US, "%.2f / h", metrics.falseWakesPerHour),
                target = "== 0.00 / h",
                passed = metrics.falseWakesPerHour <= TARGET_FALSE_WAKES_PER_HOUR,
            ),
            CriterionResult(
                id = "barge_in_latency",
                label = "Corte full-duplex por barge-in",
                measured = "${metrics.bargeInStopLatencyMs} ms",
                target = "<= $TARGET_BARGE_IN_STOP_MS ms",
                passed = metrics.bargeInStopLatencyMs in 1..TARGET_BARGE_IN_STOP_MS,
            ),
            CriterionResult(
                id = "ui_fluidity_p95",
                label = "Fluidez visual del avatar (p95 frame)",
                measured = String.format(Locale.US, "%.2f ms", metrics.uiFrameP95Ms),
                target = "<= $TARGET_UI_FRAME_P95_MS ms (60 FPS)",
                passed = metrics.uiFrameP95Ms > 0.0 && metrics.uiFrameP95Ms <= TARGET_UI_FRAME_P95_MS,
            ),
            CriterionResult(
                id = "battery_drain",
                label = "Consumo energético en escucha pasiva",
                measured = String.format(Locale.US, "%.2f %%/h", metrics.batteryDrainPercentPerHour),
                target = "<= $TARGET_BATTERY_DRAIN_PCT_PER_HOUR %/h",
                passed = metrics.batteryDrainPercentPerHour >= 0.0 &&
                    metrics.batteryDrainPercentPerHour <= TARGET_BATTERY_DRAIN_PCT_PER_HOUR,
            ),
            CriterionResult(
                id = "echo_rejection",
                label = "Rechazo acústico de eco (AEC/Duplex)",
                measured = String.format(Locale.US, "%.1f dB", metrics.echoRejectionDb),
                target = ">= $TARGET_ECHO_REJECTION_DB dB",
                passed = metrics.echoRejectionDb >= TARGET_ECHO_REJECTION_DB,
            ),
        )

        val passedCount = criteria.count { it.passed }
        val scorePercent = ((passedCount * 100.0) / criteria.size).toInt()
        val passedAll = passedCount == criteria.size
        val recommended = recommendVoiceTuning(metrics, currentTuning)
        val summary = if (passedAll) {
            "Protocolo físico aprobado al 100 % ($passedCount/${criteria.size} criterios en rango óptimo)."
        } else {
            val failed = criteria.filterNot { it.passed }.joinToString(", ") { "${it.label} (${it.measured})" }
            "Protocolo físico al $scorePercent % ($passedCount/${criteria.size}). Requiere ajuste en: $failed."
        }
        return BenchmarkReport(
            passedAll = passedAll,
            scorePercent = scorePercent,
            criteria = criteria,
            recommendedTuning = recommended,
            summary = summary,
        )
    }

    /**
     * Ajusta los umbrales de `LeoVoiceTuningProfile` según las mediciones reales del teléfono:
     * - Si la latencia de activación es alta (> 700 ms), agiliza `passiveProbeCooldownMs` y `minPassiveSpeechMs`.
     * - Si hay falsas activaciones o bajo rechazo de eco (< 18 dB), endurece `keywordThreshold`, `keywordScore` y `passiveMinimumUsefulRms`.
     * - Si el consumo de batería supera 6 %/h, incrementa moderadamente `passiveProbeCooldownMs`.
     */
    fun recommendVoiceTuning(
        metrics: BenchmarkMetrics,
        current: LeoVoiceTuningProfile = LeoVoiceTuningProfile(),
    ): LeoVoiceTuningProfile {
        var next = current
        if (metrics.wakeLatencyP95Ms > TARGET_WAKE_P95_MS) {
            next = next.copy(
                minPassiveSpeechMs = next.minPassiveSpeechMs - 20,
                passiveProbeCooldownMs = next.passiveProbeCooldownMs - 80L,
                keywordThreshold = next.keywordThreshold - 0.01f,
            )
        }
        if (metrics.falseWakesPerHour > 0.0 || metrics.echoRejectionDb < TARGET_ECHO_REJECTION_DB) {
            next = next.copy(
                keywordScore = next.keywordScore + 0.05f,
                keywordThreshold = next.keywordThreshold + 0.015f,
                passiveMinimumUsefulRms = next.passiveMinimumUsefulRms + 0.00015f,
                trailingBlanks = next.trailingBlanks + 1,
            )
        }
        if (metrics.batteryDrainPercentPerHour > TARGET_BATTERY_DRAIN_PCT_PER_HOUR) {
            next = next.copy(
                passiveProbeCooldownMs = next.passiveProbeCooldownMs + 90L,
            )
        }
        return next.sanitized()
    }

    fun toCsvRow(deviceModel: String, metrics: BenchmarkMetrics): String {
        val safeDevice = deviceModel.replace(',', ' ').trim().ifBlank { "Android-Device" }
        val report = evaluate(metrics)
        return listOf(
            safeDevice,
            metrics.wakeLatencyP95Ms.toString(),
            String.format(Locale.US, "%.2f", metrics.falseWakesPerHour),
            metrics.bargeInStopLatencyMs.toString(),
            String.format(Locale.US, "%.2f", metrics.uiFrameP95Ms),
            String.format(Locale.US, "%.2f", metrics.batteryDrainPercentPerHour),
            String.format(Locale.US, "%.1f", metrics.echoRejectionDb),
            if (report.passedAll) "PASS" else "PARTIAL_${report.scorePercent}",
        ).joinToString(",")
    }
}
