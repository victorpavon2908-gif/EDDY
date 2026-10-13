package com.niko.assistant.localai

import java.nio.file.Files
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LeoFrozenUpdatePolicyTest {
    @Test fun resumesSameReleaseButNeverMixesTwoBuilds() {
        val root = Files.createTempDirectory("leo-update").toFile()
        try {
            val part = File(root, "brain.part")
            LeoFrozenUpdatePolicy.preparePartial(part, "a".repeat(64))
            part.writeText("downloaded bytes")
            LeoFrozenUpdatePolicy.preparePartial(part, "a".repeat(64))
            assertEquals("downloaded bytes", part.readText())
            LeoFrozenUpdatePolicy.preparePartial(part, "b".repeat(64))
            assertFalse(part.exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun discardsLegacyPartialWithoutRevision() {
        val root = Files.createTempDirectory("leo-update").toFile()
        try {
            val part = File(root, "brain.part").apply { writeText("old bytes") }
            LeoFrozenUpdatePolicy.preparePartial(part, "a".repeat(64))
            assertFalse(part.exists())
        } finally { root.deleteRecursively() }
    }
}
