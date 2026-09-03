package com.niko.assistant.voice

import android.content.Context
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Local-only voice telemetry. It stores metrics and text, never PCM/audio recordings.
 * Live audio/noise values stay in memory; labeled counters survive process restarts.
 */
object LeoVoiceDiagnostics {
    data class Snapshot(
        val wakeState: String = "Sin iniciar",
        val audioLevelDbfs: Float = -96f,
        val noiseFloorDbfs: Float = -96f,
        val snrDb: Float = 0f,
        val wakeLatencyMs: Long = 0L,
        val lastTranscript: String = "",
        val transcriptionEngine: String = "Sin transcripción",
        val transcriptionLatencyMs: Long = 0L,
        val ownerScore: Float = 0f,
        val ownerAccepted: Boolean = false,
        val ownerProfileEnabled: Boolean = false,
        val microphoneInterruptions: Int = 0,
        val microphoneRecoveries: Int = 0,
        val metrics: LeoWakeMetrics = LeoWakeMetrics(),
        val scenario: LeoVoiceScenario = LeoVoiceScenario.NORMAL,
        val sessionActive: Boolean = false,
        val expectedWakeUntilElapsed: Long = 0L,
        val falsePositiveWatchActive: Boolean = false,
        val falsePositiveWatchStartedElapsed: Long = 0L,
        val tuning: LeoVoiceTuningProfile = LeoVoiceTuningProfile(),
        val recommendation: LeoVoiceTuningProfile = LeoVoiceTuningProfile(),
    )

    private const val PREFS = "leo_voice_diagnostics_v1"
    private const val EXPECTED_WAKE_MS = 7_000L
    private const val TARGET_CALLS = 100
    private val lock = Any()

    @Volatile private var appContext: Context? = null
    @Volatile private var audioRms = 0f
    @Volatile private var noiseRms = 0.0012f
    @Volatile private var lastSpeechStartElapsed = 0L
    @Volatile private var lastLoudFrameElapsed = 0L
    @Volatile private var wakeState = "Sin iniciar"
    @Volatile private var wakeLatencyMs = 0L
    @Volatile private var lastTranscript = ""
    @Volatile private var transcriptionEngine = "Sin transcripción"
    @Volatile private var transcriptionLatencyMs = 0L
    @Volatile private var ownerScore = 0f
    @Volatile private var ownerAccepted = false
    @Volatile private var ownerProfileEnabled = false
    @Volatile private var previousInputState = "STOPPED"
    @Volatile private var interruptedSinceReady = false
    @Volatile private var expectedWakeStartedElapsed = 0L
    @Volatile private var expectedWakeUntilElapsed = 0L
    @Volatile private var selectedScenario = LeoVoiceScenario.NORMAL
    @Volatile private var sessionActive = false
    @Volatile private var fpWatchStartedElapsed = 0L

    fun configure(context: Context) {
        appContext = context.applicationContext
        LeoVoiceTuning.configure(context)
        val prefs = prefs() ?: return
        selectedScenario = runCatching {
            LeoVoiceScenario.valueOf(prefs.getString("scenario", LeoVoiceScenario.NORMAL.name).orEmpty())
        }.getOrDefault(LeoVoiceScenario.NORMAL)
        sessionActive = prefs.getBoolean("session_active", false)
        fpWatchStartedElapsed = 0L
    }

    fun observeAudio(samples: FloatArray, activeCommand: Boolean) {
        if (samples.isEmpty()) return
        var sum = 0.0
        for (sample in samples) sum += sample
        val mean = sum / samples.size
        var energy = 0.0
        for (sample in samples) {
            val centered = sample - mean
            energy += centered * centered
        }
        val rms = sqrt(energy / samples.size).toFloat().coerceAtLeast(0f)
        audioRms = rms
        val now = elapsedNow()
        if (!activeCommand) {
            val quietCandidate = rms <= noiseRms * 1.8f || rms < 0.004f
            if (quietCandidate) noiseRms = (noiseRms * 0.985f + rms * 0.015f).coerceIn(0.00008f, 0.02f)
        }
        val speechGate = maxOf(0.0012f, noiseRms * if (activeCommand) 1.35f else 1.8f)
        if (rms >= speechGate) {
            if (lastSpeechStartElapsed == 0L || now - lastLoudFrameElapsed > 650L) lastSpeechStartElapsed = now
            lastLoudFrameElapsed = now
        }
        expireExpectedTrialIfNeeded(now)
    }

    fun currentNoiseRms(): Float = noiseRms

    fun recordWake(source: String = "KWS/Canary local") {
        synchronized(lock) {
            val now = elapsedNow()
            expireExpectedTrialIfNeededLocked(now)
            wakeState = "Activado · $source"
            wakeLatencyMs = if (lastSpeechStartElapsed > 0L && now - lastSpeechStartElapsed in 0..4_500L) {
                now - lastSpeechStartElapsed
            } else 0L
            val metrics = readMetricsLocked()
            val updated = when {
                expectedWakeUntilElapsed > now -> {
                    val quiet = selectedScenario in setOf(LeoVoiceScenario.QUIET, LeoVoiceScenario.WHISPER)
                    expectedWakeStartedElapsed = 0L
                    expectedWakeUntilElapsed = 0L
                    metrics.copy(
                        truePositives = metrics.truePositives + 1,
                        completedCalls = (metrics.completedCalls + 1).coerceAtMost(TARGET_CALLS),
                        quietTruePositives = metrics.quietTruePositives + if (quiet) 1 else 0,
                    )
                }
                fpWatchStartedElapsed > 0L -> metrics.copy(falsePositives = metrics.falsePositives + 1)
                else -> metrics.copy(unlabeledWakes = metrics.unlabeledWakes + 1)
            }
            writeMetricsLocked(updated)
            if (updated.completedCalls >= TARGET_CALLS) sessionActive = false
            persistSessionLocked()
        }
    }

    fun recordTranscript(text: String, engine: String, latencyMs: Long, clarification: Boolean) {
        lastTranscript = text.take(1_500)
        transcriptionEngine = if (clarification) "$engine · requiere repetir" else engine
        transcriptionLatencyMs = latencyMs.coerceAtLeast(0L)
    }

    fun recordOwnerMatch(score: Float, accepted: Boolean, enabled: Boolean) {
        ownerScore = score.coerceIn(-1f, 1f)
        ownerAccepted = accepted
        ownerProfileEnabled = enabled
    }

    fun recordInputState(state: String, status: String) {
        synchronized(lock) {
            expireExpectedTrialIfNeededLocked(elapsedNow())
            wakeState = "$state · ${status.take(100)}"
            if (state == "ERROR" && previousInputState != "ERROR") {
                increment("mic_interruptions")
                interruptedSinceReady = true
            }
            if (state == "READY" && interruptedSinceReady) {
                increment("mic_recoveries")
                interruptedSinceReady = false
            }
            previousInputState = state
        }
    }

    fun startHundredCallSession() = synchronized(lock) {
        writeMetricsLocked(LeoWakeMetrics(targetCalls = TARGET_CALLS))
        sessionActive = true
        expectedWakeStartedElapsed = 0L
        expectedWakeUntilElapsed = 0L
        persistSessionLocked()
    }

    fun selectScenario(value: LeoVoiceScenario) = synchronized(lock) {
        selectedScenario = value
        prefs()?.edit()?.putString("scenario", value.name)?.apply()
    }

    fun prepareExpectedWake(): Boolean {
        return synchronized(lock) {
            val metrics = readMetricsLocked()
            if (!sessionActive || metrics.completedCalls >= TARGET_CALLS) {
                false
            } else {
                val now = elapsedNow()
                expectedWakeStartedElapsed = now
                expectedWakeUntilElapsed = now + EXPECTED_WAKE_MS
                true
            }
        }
    }

    fun markExpectedWakeMissed() {
        synchronized(lock) {
            if (expectedWakeUntilElapsed > 0L) completeMissLocked()
        }
    }

    fun expireExpectedTrialIfNeeded(now: Long = elapsedNow()) = synchronized(lock) {
        expireExpectedTrialIfNeededLocked(now)
    }

    fun startFalsePositiveWatch() = synchronized(lock) {
        if (fpWatchStartedElapsed <= 0L) fpWatchStartedElapsed = elapsedNow()
    }

    fun stopFalsePositiveWatch() {
        synchronized(lock) {
            if (fpWatchStartedElapsed > 0L) {
                val elapsed = elapsedNow() - fpWatchStartedElapsed
                val current = readMetricsLocked()
                writeMetricsLocked(current.copy(falsePositiveWatchMs = current.falsePositiveWatchMs + elapsed.coerceAtLeast(0L)))
                fpWatchStartedElapsed = 0L
            }
        }
    }

    fun applyRecommendation() {
        val snap = snapshot()
        LeoVoiceTuning.apply(snap.recommendation)
    }

    fun resetTuning() = LeoVoiceTuning.reset()

    fun snapshot(now: Long = elapsedNow()): Snapshot = synchronized(lock) {
        expireExpectedTrialIfNeededLocked(now)
        val metrics = readMetricsLocked()
        val tuning = LeoVoiceTuning.current()
        val liveFpMs = if (fpWatchStartedElapsed > 0L) now - fpWatchStartedElapsed else 0L
        val metricsWithLiveWatch = metrics.copy(falsePositiveWatchMs = metrics.falsePositiveWatchMs + liveFpMs.coerceAtLeast(0L))
        Snapshot(
            wakeState = wakeState,
            audioLevelDbfs = dbfs(audioRms),
            noiseFloorDbfs = dbfs(noiseRms),
            snrDb = (dbfs(audioRms) - dbfs(noiseRms)).coerceIn(-12f, 60f),
            wakeLatencyMs = wakeLatencyMs,
            lastTranscript = lastTranscript,
            transcriptionEngine = transcriptionEngine,
            transcriptionLatencyMs = transcriptionLatencyMs,
            ownerScore = ownerScore,
            ownerAccepted = ownerAccepted,
            ownerProfileEnabled = ownerProfileEnabled,
            microphoneInterruptions = prefs()?.getInt("mic_interruptions", 0) ?: 0,
            microphoneRecoveries = prefs()?.getInt("mic_recoveries", 0) ?: 0,
            metrics = metricsWithLiveWatch,
            scenario = selectedScenario,
            sessionActive = sessionActive,
            expectedWakeUntilElapsed = expectedWakeUntilElapsed,
            falsePositiveWatchActive = fpWatchStartedElapsed > 0L,
            falsePositiveWatchStartedElapsed = fpWatchStartedElapsed,
            tuning = tuning,
            recommendation = LeoVoiceTuningAdvisor.recommend(
                tuning,
                metricsWithLiveWatch,
                averageSnrDb = (dbfs(audioRms) - dbfs(noiseRms)).coerceIn(-12f, 60f),
            ),
        )
    }

    private fun expireExpectedTrialIfNeededLocked(now: Long) {
        if (expectedWakeUntilElapsed > 0L && now >= expectedWakeUntilElapsed) completeMissLocked()
    }

    private fun completeMissLocked() {
        val current = readMetricsLocked()
        val quiet = selectedScenario in setOf(LeoVoiceScenario.QUIET, LeoVoiceScenario.WHISPER)
        writeMetricsLocked(current.copy(
            falseNegatives = current.falseNegatives + 1,
            completedCalls = (current.completedCalls + 1).coerceAtMost(TARGET_CALLS),
            quietFalseNegatives = current.quietFalseNegatives + if (quiet) 1 else 0,
        ))
        expectedWakeStartedElapsed = 0L
        expectedWakeUntilElapsed = 0L
        if (current.completedCalls + 1 >= TARGET_CALLS) sessionActive = false
        persistSessionLocked()
    }

    private fun readMetricsLocked(): LeoWakeMetrics {
        val p = prefs()
        return LeoWakeMetrics(
            truePositives = p?.getInt("tp", 0) ?: 0,
            falsePositives = p?.getInt("fp", 0) ?: 0,
            falseNegatives = p?.getInt("fn", 0) ?: 0,
            unlabeledWakes = p?.getInt("unlabeled", 0) ?: 0,
            targetCalls = TARGET_CALLS,
            completedCalls = p?.getInt("completed", 0) ?: 0,
            falsePositiveWatchMs = p?.getLong("fp_watch_ms", 0L) ?: 0L,
            quietTruePositives = p?.getInt("quiet_tp", 0) ?: 0,
            quietFalseNegatives = p?.getInt("quiet_fn", 0) ?: 0,
        )
    }

    private fun writeMetricsLocked(value: LeoWakeMetrics) {
        prefs()?.edit()
            ?.putInt("tp", value.truePositives)
            ?.putInt("fp", value.falsePositives)
            ?.putInt("fn", value.falseNegatives)
            ?.putInt("unlabeled", value.unlabeledWakes)
            ?.putInt("completed", value.completedCalls)
            ?.putLong("fp_watch_ms", value.falsePositiveWatchMs)
            ?.putInt("quiet_tp", value.quietTruePositives)
            ?.putInt("quiet_fn", value.quietFalseNegatives)
            ?.apply()
    }

    private fun persistSessionLocked() {
        prefs()?.edit()?.putBoolean("session_active", sessionActive)?.apply()
    }

    private fun increment(key: String) {
        val p = prefs() ?: return
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    private fun prefs() = appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun elapsedNow(): Long = System.nanoTime() / 1_000_000L
    private fun dbfs(rms: Float): Float = if (rms <= 0.000001f) -96f else (20.0 * log10(rms.toDouble())).toFloat().coerceIn(-96f, 0f)
}
