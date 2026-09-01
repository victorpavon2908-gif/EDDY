package com.eddy.assistant.voice

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EddyKeywordConfigTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun keywordsExistBeforeNativeConstruction() {
        val config = EddyKeywordConfig.create(temporary.newFolder("models"), temporary.newFolder("config"))
        val file = File(config.keywordsFile)
        assertTrue(file.isAbsolute)
        assertTrue(file.isFile)
        assertTrue(file.readLines().all { it.endsWith("@EDDY") })
        assertTrue(file.readText().isNotBlank())
    }

    @Test fun staleOrEmptyKeywordFileIsRepairedWithoutChangingModels() {
        val models = temporary.newFolder("models")
        val marker = File(models, "installed-model").apply { writeText("existing weights") }
        val directory = temporary.newFolder("config")
        File(directory, "eddy-keywords.txt").writeText("")
        val file = File(EddyKeywordConfig.create(models, directory).keywordsFile)
        assertTrue(file.readText().contains("EH1 D IY0"))
        file.writeText("STALE")
        EddyKeywordConfig.create(models, directory)
        assertFalse(file.readText().contains("STALE"))
        assertEquals("existing weights", marker.readText())
    }

    @Test fun unwritableConfigurationFailsBeforeConstructingTheDetector() {
        val notADirectory = temporary.newFile("blocked")
        assertThrows(IllegalStateException::class.java) {
            EddyKeywordConfig.create(temporary.root, notADirectory)
        }
    }
}
