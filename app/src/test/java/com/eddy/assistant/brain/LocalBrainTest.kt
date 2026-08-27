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
        assertEquals("compra comida", (result as AssistantCommand.Unknown).originalText)
    }

    @Test
    fun createsEveningAlarm() {
        assertEquals(
            AssistantCommand.SetAlarm(19, 30, "Alarma creada por EDDY"),
            brain.understand("pon una alarma a las 7:30 pm")
        )
    }

    @Test
    fun createsTimerInMinutes() {
        assertEquals(
            AssistantCommand.SetTimer(180, "Temporizador creado por EDDY"),
            brain.understand("pon un temporizador de 3 minutos")
        )
    }

    @Test
    fun composesMessageWithoutSendingAutomatically() {
        assertEquals(
            AssistantCommand.ComposeMessage("+50588887777", "voy en camino"),
            brain.understand("envía un mensaje al +505 8888 7777 diciendo voy en camino")
        )
    }

    @Test
    fun opensMapDestination() {
        assertEquals(
            AssistantCommand.OpenMaps("Masaya"),
            brain.understand("llévame a Masaya")
        )
    }

    @Test
    fun clearsMemoryOnExplicitRequest() {
        assertEquals(
            AssistantCommand.ClearMemory,
            brain.understand("EDDY olvida todo")
        )
    }
}
