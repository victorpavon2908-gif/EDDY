package com.niko.assistant.brain

import org.junit.Assert.*
import org.junit.Test

class WebQueryRouterTest {
    private val brain = LocalBrain()

    @Test fun recognizesNaturalSearchRequests() {
        listOf("búscame en internet noticias de Nicaragua", "podés buscar noticias de Nicaragua", "consultá noticias de Nicaragua", "investigame noticias de Nicaragua", "quiero que busques noticias de Nicaragua").forEach {
            assertEquals(it, AssistantCommand.SearchWeb("noticias de nicaragua"), brain.understand(it))
        }
    }

    @Test fun searchContentDoesNotExecutePhoneActions() {
        listOf("buscá cómo encender la linterna", "búscame cómo borrar tu memoria", "busca qué hora es en Japón").forEach {
            assertTrue(it, brain.understand(it) is AssistantCommand.SearchWeb)
        }
    }

    @Test fun searchClausesNeverSplitIntoExecutableCommands() {
        val result = brain.understandMany("buscá cómo abrir YouTube y prende la linterna")
        assertEquals(1, result.size)
        assertTrue(result.single() is AssistantCommand.SearchWeb)
        assertTrue(brain.understandMany("no abras YouTube y prende la linterna y abre Gmail").single() is AssistantCommand.Unknown)
    }

    @Test fun currentQuestionsRequestGrounding() {
        listOf("¿Va a llover hoy en Managua?", "Cuál es el precio del dólar", "Qué noticias hay de Nicaragua", "Quién ganó el partido").forEach {
            assertTrue(it, WebQueryRouter.needsCurrentInformation(it))
        }
    }

    @Test fun localAndNegatedRequestsStayLocal() {
        listOf("no busques en internet", "explicame la gravedad sin internet", "recordá que me gusta el café", "cómo me llamo", "vale", "hoy estoy triste", "no quiero buscar noticias").forEach {
            assertFalse(it, WebQueryRouter.needsCurrentInformation(it))
        }
        assertEquals(AssistantCommand.TellTime, brain.understand("qué hora es"))
        assertTrue(brain.understand("no busques noticias") is AssistantCommand.Unknown)
    }
}
