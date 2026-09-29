package com.niko.assistant.ai

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
class NikoAiSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    @Before fun resetPreferences() { context.getSharedPreferences("eddy_ai_settings", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun oldGeminiCredentialsAreNeverSentToGroq() {
        context.getSharedPreferences("eddy_ai_settings", Context.MODE_PRIVATE).edit()
            .putString("gemini_api_key", "old-provider-key").putString("gemini_model", "gemini-old").commit()
        assertEquals("", NikoAiSettings.apiKey(context))
        assertEquals(GroqProtocol.DEFAULT_MODEL, NikoAiSettings.model(context))
        assertFalse(NikoGroqClient(context).isConfigured)
    }

    @Test fun switchingCredentialsKeepsPersonalityAndLocalLearningPreferences() {
        NikoAiSettings.saveBehavior(context, NikoPersonality.DIRECT, false, false, true)
        NikoAiSettings.saveGroq(context, " test-key ", " ")
        assertEquals("test-key", NikoAiSettings.apiKey(context))
        assertEquals(GroqProtocol.DEFAULT_MODEL, NikoAiSettings.model(context))
        assertEquals(NikoPersonality.DIRECT, NikoAiSettings.personality(context))
        assertFalse(NikoAiSettings.localFirst(context))
        assertFalse(NikoAiSettings.autoResearch(context))
        assertTrue(NikoAiSettings.adaptiveLearning(context))
        NikoAiSettings.clearGroq(context)
        assertEquals("", NikoAiSettings.apiKey(context))
        assertEquals(NikoPersonality.DIRECT, NikoAiSettings.personality(context))
    }
}
