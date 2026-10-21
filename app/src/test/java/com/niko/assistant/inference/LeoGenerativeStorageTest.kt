package com.niko.assistant.inference

import com.niko.assistant.localai.LeoBrainStorage
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
class LeoGenerativeStorageTest {
    @Test fun modelAndPartialFilesCountAgainstSharedQuotaWithoutDeletingMemory() {
        val storage = LeoBrainStorage(RuntimeEnvironment.getApplication())
        storage.root.deleteRecursively()
        try {
            storage.adaptive.mkdirs(); storage.generative.mkdirs()
            val memory = File(storage.adaptive, "memory.txt").apply { writeText("pizza") }
            File(storage.generative, "candidate.part").writeBytes(ByteArray(10))
            File(storage.generative, "current.gguf").writeBytes(ByteArray(20))
            assertEquals(35L, storage.usage().totalBytes)
            assertEquals("pizza", memory.readText())
            RandomAccessFile(File(storage.generative, "quota.gguf"), "rw").use { it.setLength(LeoBrainStorage.TOTAL_MAX_BYTES) }
            assertFalse(storage.canGrowAdaptive(1))
            assertFalse(storage.canInstallFrozen(500_000_000, 500_000_000))
            assertEquals("pizza", memory.readText())
        } finally { storage.root.deleteRecursively() }
    }
}
