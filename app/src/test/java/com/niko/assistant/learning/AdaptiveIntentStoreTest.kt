package com.niko.assistant.learning

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdaptiveIntentStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun reloadsWeightsAndRecoversPreviousCheckpointAfterCorruption() {
        val dir = folder.newFolder()
        val store = AdaptiveIntentStore(dir)
        val model = OnlineIntentNetwork(4)
        model.learn("noticias de hoy", LearnedIntent.SEARCH)
        store.save(model)
        assertEquals(model.observations, AdaptiveIntentStore(dir).load().observations)
        model.learn("abre la camara", LearnedIntent.ACTION)
        store.save(model)
        File(dir, "intent-network.bin").writeText("incomplete")
        assertEquals(1L, store.load().observations)
    }

    @Test fun clearingRemovesBothWeightsAndRecoveryCopy() {
        val dir = folder.newFolder()
        val store = AdaptiveIntentStore(dir)
        val model = OnlineIntentNetwork(4)
        store.save(model); model.learn("hola", LearnedIntent.CONVERSATION); store.save(model)
        store.clear()
        assertTrue(dir.listFiles().orEmpty().isEmpty())
        assertEquals(0L, store.load().observations)
    }

    @Test fun unreadableLearningIsPreservedInsteadOfSilentlyReinitialized() {
        val dir = folder.newFolder()
        File(dir, "intent-network.bin").writeText("corrupt")
        assertThrows(IllegalStateException::class.java) { AdaptiveIntentStore(dir).load() }
        assertEquals("corrupt", File(dir, "intent-network.bin").readText())
    }
}
