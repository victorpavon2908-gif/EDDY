package com.niko.assistant.memory

import com.niko.assistant.compat.UpgradeIdentity
import com.niko.assistant.learning.AdaptiveIntentStore
import com.niko.assistant.learning.LearnedActionStore
import com.niko.assistant.learning.OnlineIntentNetwork
import com.niko.assistant.localai.LeoBrainStorage
import com.niko.assistant.modes.LeoMode
import com.niko.assistant.modes.LeoModeStore
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoPersonalDataResetTest {
    @Test fun aFailedStoreStillClearsOtherDataAndReleasesTheResetState() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val blocker = File(context.filesDir, "adaptive_learning/intent-network.bin").apply { mkdirs() }
        File(blocker, "cannot-delete-as-a-file").writeText("fixture")
        val sovereign = File(context.filesDir, "leo_sovereign_state.dat").apply { writeText("fixture") }
        var after = false
        try {
            assertTrue(runCatching { LeoPersonalDataReset.clear(context, after = { after = true }) }.isFailure)
            assertTrue(after)
            assertFalse(LeoPersonalDataReset.clearing)
            assertFalse(sovereign.exists())
        } finally { blocker.deleteRecursively() }
    }

    @Test fun erasesLearningAndHistoryWhilePreservingModesAndModels() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val memory = NikoMemory(context)
        memory.rememberUserTurn("Recordá que mi color favorito es azul")
        memory.rememberAssistantTurn("Guardado")
        val directory = File(context.filesDir, "adaptive_learning")
        val actions = LearnedActionStore(directory)
        assertTrue(actions.remember("ilumina la sala", "TORCH|ON"))
        AdaptiveIntentStore(directory).save(OnlineIntentNetwork.pretrained())
        File(context.filesDir, "leo_sovereign_state.dat").writeText("fixture")
        val model = File(LeoBrainStorage(context).generative, "keep.gguf").apply { writeText("model fixture") }
        LeoModeStore.set(context, LeoMode.SUPER)
        val aiPrefs = context.getSharedPreferences(UpgradeIdentity.aiPreferences, android.content.Context.MODE_PRIVATE)
        aiPrefs.edit().putBoolean("auto_research", false).commit()
        var before = false
        var after = false
        LeoPersonalDataReset.clear(context, before = { before = true; assertTrue(LeoPersonalDataReset.clearing) },
            after = { after = true })
        assertTrue(before && after)
        assertFalse(LeoPersonalDataReset.clearing)
        assertEquals(0, actions.count())
        assertFalse(File(directory, "intent-network.bin").exists())
        assertFalse(File(context.filesDir, "leo_sovereign_state.dat").exists())
        assertTrue(memory.historyForAi("").isEmpty())
        assertTrue(context.getSharedPreferences(UpgradeIdentity.memoryPreferences, android.content.Context.MODE_PRIVATE).all.isEmpty())
        assertEquals(LeoMode.SUPER, LeoModeStore.read(context))
        assertEquals("model fixture", model.readText())
        assertFalse(aiPrefs.getBoolean("auto_research", true))
    }
}
