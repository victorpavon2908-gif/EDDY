package com.niko.assistant.localai

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class LeoDirectoryActivationTest {
    @Test fun failedActivationRestoresOldContentAndSuccessfulRetryPublishesNewContent() {
        val root = Files.createTempDirectory("leo-activation-").toFile()
        try {
            val active = File(root, "frozen").apply { mkdir() }
            val candidate = File(root, "frozen.installing").apply { mkdir() }
            File(active, "database").writeText("old")
            File(candidate, "database").writeText("new")
            assertThrows(IllegalStateException::class.java) {
                LeoDirectoryActivation.activate(candidate, active) { from, to -> from != candidate && from.renameTo(to) }
            }
            assertEquals("old", File(active, "database").readText())
            assertTrue(candidate.exists())
            LeoDirectoryActivation.activate(candidate, active)
            assertEquals("new", File(active, "database").readText())
            assertFalse(File(root, "frozen.previous").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun interruptedRenameCanRecoverEvenIfStorageCreatedAnEmptyActiveDirectory() {
        val root = Files.createTempDirectory("leo-recovery-").toFile()
        try {
            val previous = File(root, "frozen.previous").apply { mkdir() }
            File(previous, "database").writeText("old")
            val active = File(root, "frozen").apply { mkdir() }
            LeoDirectoryActivation.recover(active)
            assertEquals("old", File(active, "database").readText())
        } finally { root.deleteRecursively() }
    }
}
