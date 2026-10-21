package com.niko.assistant.inference

import android.content.Context
import com.niko.assistant.inference.local.LeoLocalModelStore
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
class LeoLocalModelLifecycleTest {
    @Test fun disableThenReenableCannotRevivePreviousResponse() {
        val app = RuntimeEnvironment.getApplication()
        val storage = LeoBrainStorage(app)
        val store = LeoLocalModelStore(app)
        val prefs = app.getSharedPreferences("leo_local_generation", Context.MODE_PRIVATE)
        try {
            RandomAccessFile(store.modelFile, "rw").use { it.setLength(LeoLocalModelStore.BYTES) }
            File(storage.generative, "${LeoLocalModelStore.SHA256}.validated").writeText("fixture")
            prefs.edit().putBoolean("enabled", true).putString("revision", "first").commit()
            val old = store.snapshot()
            assertTrue(store.isCurrent(old))
            store.disable()
            assertFalse(store.isCurrent(old))
            prefs.edit().putBoolean("enabled", true).putString("revision", "second").commit()
            assertTrue(store.enabled)
            assertFalse(store.isCurrent(old))
            assertTrue(store.isCurrent(store.snapshot()))
        } finally { storage.root.deleteRecursively(); prefs.edit().clear().commit() }
    }
    @Test fun removingModelPreservesPersonalMemoryAndOtherModels() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        val storage = LeoBrainStorage(app)
        val store = LeoLocalModelStore(app)
        try {
            val memory = File(storage.adaptive, "memory.txt").apply { writeText("comida favorita: pizza") }
            val other = File(storage.generative, "other-model.gguf").apply { writeText("untouched") }
            store.modelFile.writeText("fixture")
            File(storage.generative, "${LeoLocalModelStore.SHA256}.part").writeText("partial")
            File(storage.generative, "${LeoLocalModelStore.SHA256}.validated").writeText("fixture")
            store.removeDownload()
            assertFalse(store.enabled)
            assertFalse(store.modelFile.exists())
            assertFalse(File(storage.generative, "${LeoLocalModelStore.SHA256}.part").exists())
            assertFalse(File(storage.generative, "${LeoLocalModelStore.SHA256}.validated").exists())
            assertEquals("comida favorita: pizza", memory.readText())
            assertEquals("untouched", other.readText())
        } finally { storage.root.deleteRecursively() }
    }
}
