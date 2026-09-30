package com.niko.assistant.media

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaFilesTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun galleryKeepsOnlyNewestNonemptyFilesInOrder() {
        val directory = folder.newFolder()
        repeat(100) { index ->
            File(directory, "$index.m4a").apply { writeText("audio"); setLastModified(100000L + index * 1000L) }
        }
        File(directory, "empty.m4a").createNewFile()
        File(directory, "directory").mkdir()
        val recent = MediaFiles.recent(directory, 3)
        assertEquals(listOf("99.m4a", "98.m4a", "97.m4a"), recent.map { it.name })
        assertTrue(MediaFiles.recent(directory, 0).isEmpty())
    }
}
