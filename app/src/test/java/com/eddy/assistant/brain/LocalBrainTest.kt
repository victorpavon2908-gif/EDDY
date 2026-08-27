package com.eddy.assistant.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBrainTest {
    private val brain = LocalBrain()

    @Test
    fun opensYoutube() {
        assertEquals(
            AssistantCommand.OpenApp(SupportedApp.YOUTUBE),
            brain.understand("EDDY, abre YouTube")
        )
    }

    @Test
    fun understandsCameraWithoutAccent() {
        assertEquals(
            AssistantCommand.OpenCamera,
            brain.understand("abre la cámara")
        )
    }

    @Test
    fun unknownCommandKeepsOriginalText() {
        val result = brain.understand("compra comida")
        assertTrue(result is AssistantCommand.Unknown)
    }
}
