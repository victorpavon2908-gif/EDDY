package com.niko.assistant.learning

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LearnedActionStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun correctionPersistsAndMatchesTheOwnersExactNormalizedPhrase() {
        val directory = folder.newFolder()
        val store = LearnedActionStore(directory)
        assertTrue(store.remember("Abríme el guasa", "OPEN_APP|WhatsApp", 10L))
        assertEquals("OPEN_APP|WhatsApp", LearnedActionStore(directory).resolve("abrime el guasa", 20L))
        assertEquals(1, LearnedActionStore(directory).count())
        assertNull(store.resolve("abrime otra aplicación"))
    }

    @Test fun rejectsSensitiveOpcodesAndRecoversThePreviousCheckpoint() {
        val directory = folder.newFolder()
        val store = LearnedActionStore(directory)
        assertFalse(store.remember("manda eso", "WHATSAPP||mensaje"))
        assertFalse(store.remember("busca eso", "SEARCH|mi contraseña es secreta"))
        assertTrue(store.remember("prende la luz", "TORCH|ON", 10L))
        assertEquals("TORCH|ON", store.resolve("prende la luz", 20L))
        File(directory, "learned-actions.bin").writeText("dañado")
        assertEquals("TORCH|ON", LearnedActionStore(directory).resolve("prende la luz", 30L))
    }

    @Test fun clearRemovesCurrentBackupAndPendingFiles() {
        val directory = folder.newFolder()
        val store = LearnedActionStore(directory)
        store.remember("prende la luz", "TORCH|ON")
        store.resolve("prende la luz")
        store.clear()
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
