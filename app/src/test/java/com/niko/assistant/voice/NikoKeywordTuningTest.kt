package com.niko.assistant.voice

import org.junit.Assert.assertTrue
import org.junit.Test

class NikoKeywordTuningTest {
    @Test fun measuredProfileChangesGeneratedKeywordDsl() {
        val profile = LeoVoiceTuningProfile(keywordScore = 1.42f, keywordThreshold = 0.09f)
        val text = NikoKeywordConfig.keywordText(profile)
        assertTrue(text.contains(":1.42 #0.09 @LEO"))
        assertTrue(text.lines().filter(String::isNotBlank).all { it.endsWith("@LEO") })
    }
}
