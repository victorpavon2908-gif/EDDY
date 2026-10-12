package com.niko.assistant.localai

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LeoDownloadIntegrityTest {
    @Test fun resumeRequiresCorrectOffsetAndTotal() {
        assertTrue(LeoDownloadIntegrity.validPartialRange("bytes 10-99/100", 10, 100))
        assertFalse(LeoDownloadIntegrity.validPartialRange("bytes 0-99/100", 10, 100))
        assertFalse(LeoDownloadIntegrity.validPartialRange("bytes 10-99/200", 10, 100))
        assertFalse(LeoDownloadIntegrity.validPartialRange("bytes 10-100/100", 10, 100))
        assertFalse(LeoDownloadIntegrity.validPartialRange(null, 10, 100))
        assertFalse(LeoDownloadIntegrity.validPartialRange("bytes 10-99/*", 10, 100))
    }

    @Test fun corruptCompleteArchiveIsRemovedSoRetryDoesNotReuseIt() {
        val file = Files.createTempFile("leo-archive-", ".part").toFile()
        try {
            file.writeText("bad")
            assertThrows(IllegalStateException::class.java) {
                LeoDownloadIntegrity.requireValidArchive(file, 3L, "good") { "bad" }
            }
            assertFalse(file.exists())
            file.writeText("new")
            LeoDownloadIntegrity.requireValidArchive(file, 3L, "good") { "good" }
            assertTrue(file.exists())
        } finally { file.delete() }
    }
}
