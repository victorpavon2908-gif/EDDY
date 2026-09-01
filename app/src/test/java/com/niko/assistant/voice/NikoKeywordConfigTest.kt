package com.niko.assistant.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NikoKeywordConfigTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun keywordsExistBeforeNativeConstruction() {
        val config = NikoKeywordConfig.create(temporary.newFolder("models"), temporary.newFolder("config"))
        val file = File(config.keywordsFile)
        assertTrue(file.isAbsolute)
        assertTrue(file.isFile)
        assertTrue(file.readLines().all { it.endsWith("@NIKO") })
        assertTrue(file.readText().isNotBlank())
    }

    @Test fun staleOrEmptyKeywordFileIsRepairedWithoutChangingModels() {
        val models = temporary.newFolder("models")
        val marker = File(models, "installed-model").apply { writeText("existing weights") }
        val directory = temporary.newFolder("config")
        File(directory, "niko-keywords.txt").writeText("")
        val file = File(NikoKeywordConfig.create(models, directory).keywordsFile)
        assertTrue(file.readText().contains("N IY1 K OW0"))
        file.writeText("STALE")
        NikoKeywordConfig.create(models, directory)
        assertFalse(file.readText().contains("STALE"))
        assertEquals("existing weights", marker.readText())
    }

    @Test fun unwritableConfigurationFailsBeforeConstructingTheDetector() {
        val notADirectory = temporary.newFile("blocked")
        assertThrows(IllegalStateException::class.java) {
            NikoKeywordConfig.create(temporary.root, notADirectory)
        }
    }
}
