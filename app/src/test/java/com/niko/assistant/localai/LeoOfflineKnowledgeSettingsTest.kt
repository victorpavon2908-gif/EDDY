package com.niko.assistant.localai

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoOfflineKnowledgeSettingsTest {
    @Test fun optionalDownloadRequiresConsentAndCanBeRevoked() = runBlocking {
        val context = RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences(com.niko.assistant.compat.UpgradeIdentity.aiPreferences, Context.MODE_PRIVATE).edit().clear().commit()
        assertFalse(LeoOfflineKnowledgeSettings.downloadAllowed(context))
        // No transport is reached: the default path finishes without network/download files.
        val states = mutableListOf<LeoFrozenBrainManager.Progress.State>()
        assertFalse(LeoFrozenBrainManager(context).ensureInstalled { states += it.state })
        assertEquals(listOf(LeoFrozenBrainManager.Progress.State.CHECKING), states)
        assertEquals(0, LeoBrainStorage(context).downloads.listFiles().orEmpty().size)
        LeoOfflineKnowledgeSettings.allowDownload(context, true)
        assertTrue(LeoOfflineKnowledgeSettings.downloadAllowed(context))
        LeoOfflineKnowledgeSettings.allowDownload(context, false)
        assertFalse(LeoOfflineKnowledgeSettings.downloadAllowed(context))
    }
}
