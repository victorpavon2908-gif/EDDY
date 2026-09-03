package com.niko.assistant.brain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoStructuredPlannerTest {
    @Test
    fun simpleLocalCommandNeverCallsGroq() = runBlocking {
        var calls = 0
        val planner = LeoStructuredPlanner(LocalBrain()) {
            calls++
            error("Groq should not be called for a known local action")
        }
        val decision = planner.plan("Leo, abrime WhatsApp")
        assertEquals(LeoPlanSource.LOCAL, decision.source)
        assertEquals(0, calls)
        assertTrue(decision.commands.single() !is AssistantCommand.Unknown)
        assertFalse(decision.requiresConfirmation)
    }

    @Test
    fun nicaraguanFreeWordingCanUseSemanticFallback() = runBlocking {
        var calls = 0
        val planner = LeoStructuredPlanner(LocalBrain()) {
            calls++
            "OPEN_APP|YouTube"
        }
        val decision = planner.plan("Leo, haceme el favor de entrar a YouTube cuando podás")
        assertEquals(1, calls)
        assertEquals(LeoPlanSource.GROQ, decision.source)
        assertEquals(listOf(AssistantCommand.OpenAppByName("YouTube")), decision.commands)
        assertTrue(decision.confidence >= 0.80)
    }

    @Test
    fun oneInvalidDslLineRejectsTheWholePlan() {
        val planner = LeoStructuredPlanner(LocalBrain()) { null }
        assertTrue(planner.parseDsl("OPEN_APP|YouTube\nDELETE_FILE|/data/user/0").isEmpty())
        assertTrue(planner.parseDsl("TORCH|ON\nBRIGHTNESS|900").isEmpty())
        assertTrue(planner.parseDsl("NONE\nOPEN_APP|WhatsApp").isEmpty())
        assertTrue(planner.parseDsl("VIBRATE|not-a-number").isEmpty())
    }

    @Test
    fun strictArityAndPhoneValidationBlockMalformedArguments() {
        val planner = LeoStructuredPlanner(LocalBrain()) { null }
        assertTrue(planner.parseDsl("CAMERA|unexpected").isEmpty())
        assertTrue(planner.parseDsl("OPEN_APP|YouTube|extra").isEmpty())
        assertTrue(planner.parseDsl("WHATSAPP|abc|Hola").isEmpty())
        assertTrue(planner.parseDsl("SMS||Hola").isEmpty())
        assertTrue(planner.parseDsl("ALARM|31|99|x").isEmpty())
    }

    @Test
    fun validCompoundDslPreservesRequestedOrder() {
        val planner = LeoStructuredPlanner(LocalBrain()) { null }
        assertEquals(
            listOf(
                AssistantCommand.OpenAppByName("YouTube"),
                AssistantCommand.AdjustVolume(VolumeDirection.DOWN),
                AssistantCommand.SetBrightness(50),
            ),
            planner.parseDsl("OPEN_APP|YouTube\nVOLUME|DOWN\nBRIGHTNESS|50"),
        )
    }

    @Test
    fun groqFailureFallsBackWithoutInventingActions() = runBlocking {
        val planner = LeoStructuredPlanner(LocalBrain()) { error("network failure") }
        val decision = planner.plan("Leo, serías tan amable de abrirme la aplicación de música")
        assertEquals(LeoPlanSource.FALLBACK, decision.source)
        assertTrue(decision.commands.single() is AssistantCommand.Unknown)
    }

    @Test
    fun questionsAndNegationsNeverReachGroq() = runBlocking {
        var calls = 0
        val planner = LeoStructuredPlanner(LocalBrain()) {
            calls++
            "OPEN_APP|YouTube"
        }
        assertTrue(planner.resolveMany("Leo, no abras YouTube").single() is AssistantCommand.Unknown)
        assertTrue(planner.resolveMany("Leo, cómo hago para apagar la linterna").single() is AssistantCommand.Unknown)
        assertTrue(planner.resolveMany("Leo, quién fue Rubén Darío").single() is AssistantCommand.Unknown)
        assertEquals(0, calls)
    }

    @Test
    fun knownCompoundActionsStayLocalAndKeepOrder() = runBlocking {
        var calls = 0
        val planner = LeoStructuredPlanner(LocalBrain()) {
            calls++
            "NONE"
        }
        val decision = planner.plan("Leo, abrí YouTube y después baja el volumen")
        assertEquals(LeoPlanSource.LOCAL, decision.source)
        assertEquals(0, calls)
        assertEquals(2, decision.commands.size)
        assertTrue(decision.commands[0] is AssistantCommand.OpenApp)
        assertEquals(AssistantCommand.AdjustVolume(VolumeDirection.DOWN), decision.commands[1])
    }

    @Test
    fun sensitiveSemanticPlanIsMarkedAndNotExposedForExecution() = runBlocking {
        val planner = LeoStructuredPlanner(LocalBrain()) { "SMART_HOME|OFF|portón principal" }
        val decision = planner.plan("Leo, haceme el favor de desactivar el portón principal")
        assertEquals(LeoPlanSource.GROQ, decision.source)
        assertEquals(LeoPlanRisk.HIGH, decision.risk)
        assertTrue(decision.requiresConfirmation)
        assertTrue(decision.commands.single() is AssistantCommand.Unknown)
    }

    @Test
    fun disabledCapabilityFromGroqIsRejected() = runBlocking {
        val planner = LeoStructuredPlanner(LocalBrain()) { "TORCH|ON" }
        val decision = planner.plan(
            LeoPlannerInput(
                utterance = "Leo, poneme YouTube en pantalla",
                capabilities = setOf("OPEN_APP"),
            ),
        )
        assertEquals(LeoPlanSource.FALLBACK, decision.source)
        assertTrue(decision.commands.single() is AssistantCommand.Unknown)
    }

    @Test
    fun plannerInputCarriesMemoryContextRecentContextAndCapabilities() = runBlocking {
        var prompt = ""
        val planner = LeoStructuredPlanner(LocalBrain()) {
            prompt = it
            "OPEN_APP|YouTube"
        }
        planner.plan(
            LeoPlannerInput(
                utterance = "Leo, porfa metete a YouTube cuando podás",
                memoryContext = "El usuario prefiere YouTube para videos.",
                recentContext = "app=Chrome",
                capabilities = setOf("OPEN_APP", "DELETE_FILE"),
            ),
        )
        assertTrue(prompt.contains("El usuario prefiere YouTube"))
        assertTrue(prompt.contains("app=Chrome"))
        assertTrue(prompt.contains("OPEN_APP"))
        assertFalse(prompt.contains("Capacidades habilitadas: DELETE_FILE"))
    }
}
