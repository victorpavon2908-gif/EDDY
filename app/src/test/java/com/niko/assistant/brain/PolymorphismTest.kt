package com.niko.assistant.brain

import org.junit.Assert.*
import org.junit.Test

class PolymorphismTest {
    private val brain = LocalBrain()
    @Test fun requestedFormsUseEmbeddedTools() {
        val cases = mapOf(
            "Eddy conviértete en una cámara" to "NIKO_TOOL_CAMERA",
            "Leo conviértete en una calculadora" to "NIKO_TOOL_CALCULATOR",
            "Leo conviértete en un cronómetro" to "NIKO_TOOL_STOPWATCH",
            "transformate en un grabador de video" to "NIKO_TOOL_VIDEO",
            "convertite en un gravador de audio" to "NIKO_TOOL_AUDIO",
            "conviertete en un reproductor de musica" to "NIKO_TOOL_MUSIC",
            "modo video" to "NIKO_TOOL_VIDEO",
        )
        cases.forEach { (phrase, tool) -> assertEquals(phrase, AssistantCommand.OpenAppByName(tool), brain.understand(phrase)) }
    }
    @Test fun unsupportedFormsBecomeGeneratedTools() {
        assertEquals(
            AssistantCommand.GenerateTool("impresora"),
            brain.understand("conviertete en una impresora"),
        )
    }
    @Test fun musicSearchHasItsOwnRoute() {
        assertEquals(AssistantCommand.OpenAppByName("LEO_MUSIC_QUERY:shakira"), brain.understand("Leo buscame musica de Shakira"))
        assertTrue(brain.understand("busca en internet historia de la musica") is AssistantCommand.SearchWeb)
    }
    @Test fun negationNeverOpensTools() {
        assertTrue(brain.understand("no te conviertas en una camara") is AssistantCommand.Unknown)
        assertTrue(brain.understand("no busques musica de shakira") is AssistantCommand.Unknown)
        assertTrue(brain.understand("explicame como convertirte en una camara") is AssistantCommand.Unknown)
    }
    @Test fun existingCameraCommandRemainsCompatible() {
        assertEquals(AssistantCommand.OpenCamera, brain.understand("abre camara"))
    }
}
