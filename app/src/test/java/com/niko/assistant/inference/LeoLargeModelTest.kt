package com.niko.assistant.inference

import android.content.Context
import com.niko.assistant.inference.local.*
import com.niko.assistant.localai.LeoBrainStorage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeoLargeModelTest {
    @Test fun legacySelectionAndCatalogRejectUnknownPaths() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("leo_local_generation", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        assertEquals(LeoLocalModelCatalog.compact, LeoLocalModelStore(app).spec)
        assertNull(LeoLocalModelCatalog.find("../../arbitrary.gguf"))
        assertEquals(4_280_404_704L, LeoLocalModelCatalog.large.bytes)
        assertTrue(LeoLocalModelCatalog.large.bytes + LeoLocalModelCatalog.compact.bytes < LeoBrainStorage.TOTAL_MAX_BYTES)
    }
    @Test fun deletingInactiveLargeModelPreservesActiveLegacyModel() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("leo_local_generation", Context.MODE_PRIVATE)
        val storage = LeoBrainStorage(app)
        prefs.edit().clear().putBoolean("enabled", true).putString("revision", "legacy").commit()
        val compact = LeoLocalModelStore(app)
        val large = LeoLocalModelStore(app, LeoLocalModelCatalog.large)
        try {
            RandomAccessFile(compact.modelFile, "rw").use { it.setLength(compact.spec.bytes) }
            File(storage.generative, "${compact.spec.sha256}.validated").writeText("fixture")
            large.modelFile.writeText("partial fixture")
            val revision = compact.snapshot()
            large.removeDownload()
            assertTrue(compact.enabled)
            assertTrue(compact.isCurrent(revision))
            assertFalse(large.modelFile.exists())
            prefs.edit().putString("model_id", LeoLocalModelCatalog.large.id).putString("revision", "new").commit()
            assertFalse(compact.isCurrent(revision))
            assertEquals(LeoLocalModelCatalog.large, LeoLocalModelStore(app).spec)
        } finally { storage.root.deleteRecursively(); prefs.edit().clear().commit() }
    }
    @Test fun googlePresetRequiresCredentialButKeepsOwnServersSupported() {
        assertFalse(LeoGoogleProvider.validCredential(LeoGoogleProvider.ENDPOINT, ""))
        assertFalse(LeoGoogleProvider.validCredential(LeoGoogleProvider.ENDPOINT.uppercase().replace("HTTPS", "https"), " "))
        assertTrue(LeoGoogleProvider.validCredential(LeoGoogleProvider.ENDPOINT, "fixture-not-a-real-key"))
        assertTrue(LeoGoogleProvider.validCredential("https://example.org/chat/completions", ""))
        assertEquals("generativelanguage.googleapis.com", LeoInferenceProtocol.endpoint(LeoGoogleProvider.ENDPOINT).host)
    }
}
