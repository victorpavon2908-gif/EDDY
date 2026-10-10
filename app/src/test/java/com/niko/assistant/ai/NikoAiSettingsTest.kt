package com.niko.assistant.ai

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NikoAiSettingsTest {
    @Test fun retiringProviderDeletesOnlyCredentialsAndIsIdempotent() {
        val context: Context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("eddy_ai_settings", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("groq_api_key", "old-secret").putString("groq_model", "old-model")
            .putString("unrelated", "keep").commit()
        NikoAiSettings.saveBehavior(context, NikoPersonality.DIRECT, false, false, true)
        repeat(2) { NikoAiSettings.retireCloudCredentials(context) }
        assertFalse(prefs.contains("groq_api_key"))
        assertFalse(prefs.contains("groq_model"))
        assertEquals("keep", prefs.getString("unrelated", null))
        assertEquals(NikoPersonality.DIRECT, NikoAiSettings.personality(context))
        assertFalse(NikoAiSettings.localFirst(context))
        assertFalse(NikoAiSettings.autoResearch(context))
        assertTrue(NikoAiSettings.adaptiveLearning(context))
    }
}
