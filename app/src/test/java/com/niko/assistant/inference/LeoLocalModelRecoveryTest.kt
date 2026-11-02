package com.niko.assistant.inference

import com.niko.assistant.inference.local.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoLocalModelRecoveryTest {
    private val spec = LeoLocalModelCatalog.compact.copy(bytes = 3,
        sha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")

    @Before fun cleanFixture() {
        val context = RuntimeEnvironment.getApplication()
        val store = LeoLocalModelStore(context, spec)
        store.modelFile.delete()
        File(store.modelFile.parentFile, "${spec.sha256}.validated").delete()
        context.getSharedPreferences("leo_local_generation", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun sameSizeCorruptionCannotAcquireAVerifiedMarker() = runBlocking {
        val store = LeoLocalModelStore(RuntimeEnvironment.getApplication(), spec)
        store.modelFile.writeText("abd")
        assertFalse(store.recoverIfVerified())
        assertFalse(store.installed)
        assertFalse(store.enabled)
        assertTrue(store.modelFile.exists())
    }
    @Test fun validRecoveryKeepsTheUsersDisabledSetting() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("leo_local_generation", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("enabled", false).putString("model_id", "google-gemma-2-2b").commit()
        val before = prefs.all.toMap()
        val store = LeoLocalModelStore(context, spec)
        store.modelFile.writeText("abc")
        assertTrue(store.recoverIfVerified())
        assertTrue(store.installed)
        assertFalse(store.enabled)
        assertEquals(before, prefs.all)
    }
    @Test fun malformedMarkerDoesNotCountAsAnInstalledModel() = runBlocking {
        val store = LeoLocalModelStore(RuntimeEnvironment.getApplication(), spec)
        store.modelFile.writeText("abc")
        File(store.modelFile.parentFile, "${spec.sha256}.validated").writeText("unverified")
        assertFalse(store.installed)
        assertTrue(store.recoverIfVerified())
        assertTrue(store.installed)
    }
    @Test fun recoveryRechecksAnExistingMarkerAfterSameSizeTampering() = runBlocking {
        val store = LeoLocalModelStore(RuntimeEnvironment.getApplication(), spec)
        store.modelFile.writeText("abc")
        assertTrue(store.recoverIfVerified())
        store.modelFile.writeText("abd")
        assertFalse(store.recoverIfVerified())
        assertFalse(store.installed)
    }

    @Test fun physicalRamAloneDoesNotMakeADeviceCompatible() {
        val model = spec.copy(minimumRam = 100, availableRam = 40)
        assertTrue(model.canRun(100, 40, false, true))
        assertFalse(model.canRun(100, 39, false, true))
        assertFalse(model.canRun(99, 100, false, true))
        assertFalse(model.canRun(100, 100, true, true))
        assertFalse(model.canRun(100, 100, false, false))
    }
}
