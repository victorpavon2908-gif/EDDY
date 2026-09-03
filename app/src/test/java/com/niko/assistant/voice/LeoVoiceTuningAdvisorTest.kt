package com.niko.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoVoiceTuningAdvisorTest {
    @Test fun neverTunesBlindlyBeforeEnoughLabeledCalls() {
        val current = LeoVoiceTuningProfile()
        val recommendation = LeoVoiceTuningAdvisor.recommend(
            current,
            LeoWakeMetrics(truePositives = 9, falseNegatives = 1, completedCalls = 10),
            averageSnrDb = 12f,
        )
        assertEquals(current, recommendation)
    }

    @Test fun missesWithLowFpRateIncreaseWakeSensitivity() {
        val current = LeoVoiceTuningProfile()
        val recommendation = LeoVoiceTuningAdvisor.recommend(
            current,
            LeoWakeMetrics(
                truePositives = 85,
                falseNegatives = 15,
                completedCalls = 100,
                falsePositiveWatchMs = 3_600_000L,
            ),
            averageSnrDb = 10f,
        )
        assertTrue(recommendation.keywordThreshold < current.keywordThreshold)
        assertTrue(recommendation.keywordScore < current.keywordScore)
        assertTrue(recommendation.minPassiveSpeechMs < current.minPassiveSpeechMs)
    }

    @Test fun measuredFalsePositivesMakeWakeMoreConservative() {
        val current = LeoVoiceTuningProfile()
        val recommendation = LeoVoiceTuningAdvisor.recommend(
            current,
            LeoWakeMetrics(
                truePositives = 97,
                falseNegatives = 3,
                falsePositives = 4,
                completedCalls = 100,
                falsePositiveWatchMs = 60 * 60_000L,
            ),
            averageSnrDb = 10f,
        )
        assertTrue(recommendation.keywordThreshold > current.keywordThreshold)
        assertTrue(recommendation.keywordScore > current.keywordScore)
        assertTrue(recommendation.trailingBlanks > current.trailingBlanks)
    }

    @Test fun quietMissesWithUsableSnrIncreaseOnlyBoundedGain() {
        val current = LeoVoiceTuningProfile()
        val recommendation = LeoVoiceTuningAdvisor.recommend(
            current,
            LeoWakeMetrics(
                truePositives = 95,
                falseNegatives = 5,
                completedCalls = 100,
                quietTruePositives = 6,
                quietFalseNegatives = 4,
            ),
            averageSnrDb = 7f,
        )
        assertTrue(recommendation.activeMaxGain > current.activeMaxGain)
        assertTrue(recommendation.activeMaxGain <= 5.5f)
        assertTrue(recommendation.passiveMaxGain <= 3.2f)
    }
}
