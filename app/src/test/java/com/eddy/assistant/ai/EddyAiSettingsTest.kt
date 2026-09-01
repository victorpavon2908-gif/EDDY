package com.eddy.assistant.ai

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class EddyAiSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun resetPreferences() { context.getSharedPreferences("eddy_ai_settings", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun oldGeminiCredentialsAreNeverSentToGroq() {
        context.getSharedPreferences("eddy_ai_settings", Context.MODE_PRIVATE).edit()
            .putString("gemini_api_key", "old-provider-key").putString("gemini_model", "gemini-old").commit()
        assertEquals("", EddyAiSettings.apiKey(context))
        assertEquals(GroqProtocol.DEFAULT_MODEL, EddyAiSettings.model(context))
        assertFalse(EddyGroqClient(context).isConfigured)
    }

    @Test fun switchingCredentialsKeepsPersonalityAndLocalLearningPreferences() {
        EddyAiSettings.saveBehavior(context, EddyPersonality.DIRECT, false, false, true)
        EddyAiSettings.saveGroq(context, " test-key ", " ")
        assertEquals("test-key", EddyAiSettings.apiKey(context))
        assertEquals(GroqProtocol.DEFAULT_MODEL, EddyAiSettings.model(context))
        assertEquals(EddyPersonality.DIRECT, EddyAiSettings.personality(context))
        assertFalse(EddyAiSettings.localFirst(context))
        assertFalse(EddyAiSettings.autoResearch(context))
        assertTrue(EddyAiSettings.adaptiveLearning(context))
        EddyAiSettings.clearGroq(context)
        assertEquals("", EddyAiSettings.apiKey(context))
        assertEquals(EddyPersonality.DIRECT, EddyAiSettings.personality(context))
    }
}
