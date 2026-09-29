package com.niko.assistant.localai

import android.content.Context
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.ai.NikoPersonality
import com.niko.assistant.background.NikoVoiceSettings
import com.niko.assistant.compat.UpgradeIdentity
import java.io.File
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class NikoUpgradeCompatibilityTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun updateKeepsGroqPreferencesAndThePausedMicrophone() {
        context.getSharedPreferences("eddy_ai_settings", Context.MODE_PRIVATE).edit()
            .putString("groq_api_key", "existing-key").putString("personality", "DIRECT").commit()
        context.getSharedPreferences("eddy_control", Context.MODE_PRIVATE).edit()
            .putBoolean("assistant_enabled", false).commit()
        assertEquals("existing-key", NikoAiSettings.apiKey(context))
        assertEquals(NikoPersonality.DIRECT, NikoAiSettings.personality(context))
        assertFalse(NikoVoiceSettings.enabled(context))
    }

    @Test fun installedKeywordModelSurvivesTheNewCatalogName() {
        val models = NikoModelManager(context)
        val spec = NikoModelCatalog.keyword
        val directory = models.modelDir(spec)
        assertEquals(File(context.filesDir, "eddy-local-ai/kws"), directory)
        spec.expectedMinBytes.forEach { (path, size) ->
            val file = File(directory, path)
            file.parentFile!!.mkdirs()
            RandomAccessFile(file, "rw").use { it.setLength(size) }
        }
        // Model files stay in place: only the installation's old revision ID differs.
        val marker = File(directory, ".eddy-model-id")
        marker.writeText("kws-eddy-zh-en-2025-v3")
        assertTrue(models.invalidReason(spec).orEmpty(), models.isInstalled(spec))
        assertEquals("kws-eddy-zh-en-2025-v3", marker.readText())
        marker.writeText("unrelated-model")
        assertFalse(models.isInstalled(spec))
    }

    @Test fun pendingIntentsStillTargetTheExistingAndroidComponents() {
        assertEquals("com.eddy.assistant.background.EddyAssistantService", UpgradeIdentity.assistantService(context).component!!.className)
        assertEquals("com.eddy.assistant.EddyWakeActivity", UpgradeIdentity.wakeActivity(context).component!!.className)
        assertEquals("com.eddy.assistant.proactive.EddyProactiveReceiver", UpgradeIdentity.proactiveReceiver(context).component!!.className)
    }
}
