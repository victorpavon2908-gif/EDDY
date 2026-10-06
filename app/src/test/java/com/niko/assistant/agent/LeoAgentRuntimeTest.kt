package com.niko.assistant.agent

import com.niko.assistant.ai.NikoAiReply
import com.niko.assistant.ai.NikoWebSource
import com.niko.assistant.brain.AssistantCommand
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LeoAgentRuntimeTest {
    @Test fun generationRejectsLateProducerEvenWithoutJobCancellation() = runBlocking {
        val turns = TurnController()
        val old = turns.begin()
        val new = turns.begin()
        assertFalse(turns.isCurrent(old)); assertTrue(turns.isCurrent(new))
        try { withContext(old) { turns.checkpoint() }; fail("Old producer accepted") }
        catch (_: CancellationException) { }
    }
    @Test fun cancellationInvalidatesWithoutNewRequest() {
        val turns = TurnController(); val old = turns.begin(); turns.cancel(); assertFalse(turns.isCurrent(old))
    }
    @Test fun foreignControllerCannotSupplyToken() {
        val first = TurnController(); val second = TurnController()
        val token = first.begin(); second.begin(); assertFalse(second.isCurrent(token))
    }
    @Test fun newerTurnSurvivesOldFinally() = runBlocking {
        val turns = TurnController(); val old = turns.begin(); val new = turns.begin()
        var published = "new"
        withContext(old) { if (turns.isCurrent(old)) published = "old" }
        withContext(new) { turns.checkpoint() }
        assertEquals("new", published)
    }
    @Test fun confirmationIsExactBoundAndOneShot() {
        val agent = LeoAgentRuntime { 100L }
        agent.requestConfirmation(AssistantCommand.ClearMemory)
        val result = agent.confirmation("Sí") as LeoAgentRuntime.ConfirmationReply.Approved
        assertEquals(AssistantCommand.ClearMemory, result.command)
        assertEquals(LeoAgentRuntime.ConfirmationReply.None, agent.confirmation("sí"))
    }
    @Test fun topicChangeRevokesConfirmation() {
        val agent = LeoAgentRuntime { 100L }; agent.requestConfirmation(AssistantCommand.ClearMemory)
        assertEquals(LeoAgentRuntime.ConfirmationReply.None, agent.confirmation("sí, pero primero abrí mapas"))
        assertEquals(LeoAgentRuntime.ConfirmationReply.None, agent.confirmation("sí"))
    }
    @Test fun confirmationExpires() {
        var now = 0L; val agent = LeoAgentRuntime { now }; agent.requestConfirmation(AssistantCommand.ClearMemory)
        now = 60_001L; assertEquals(LeoAgentRuntime.ConfirmationReply.None, agent.confirmation("sí"))
    }
    @Test fun interruptionRevokesConfirmation() {
        val agent = LeoAgentRuntime(); agent.requestConfirmation(AssistantCommand.ClearMemory); agent.interrupt()
        assertEquals(LeoAgentRuntime.ConfirmationReply.None, agent.confirmation("sí"))
    }
    private fun research(manager: ConversationManager) {
        manager.begin("Buscame OpenAI hoy", 1)
        val reply = NikoAiReply("Anunció un modelo [1].", true, listOf(NikoWebSource("Comunicado", "https://openai.com/news")))
        manager.researched("OpenAI hoy", reply); manager.reply(reply)
    }
    @Test fun relatedEntityKeepsPublicResearchTopic() {
        val manager = ConversationManager { 100L }; research(manager)
        manager.begin("¿Y Microsoft qué dijo?", 2)
        assertTrue(manager.researchQuery("¿Y Microsoft qué dijo?").contains("OpenAI hoy"))
        assertTrue(manager.shouldResearchFollowUp("¿Y Microsoft qué dijo?"))
    }
    @Test fun simplificationKeepsActualResultWithoutResearch() {
        val manager = ConversationManager { 100L }; research(manager)
        manager.begin("Eso último explicámelo más sencillo", 2)
        assertEquals("simple", manager.state.detail)
        assertTrue(manager.context().contains("Anunció un modelo"))
        assertFalse(manager.shouldResearchFollowUp("Eso último explicámelo más sencillo"))
    }
    @Test fun unrelatedQueryDoesNotInheritResearch() {
        val manager = ConversationManager { 100L }; research(manager)
        assertEquals("precio del café", manager.researchQuery("precio del café"))
    }
    @Test fun staleTopicDoesNotBecomeSearchQuery() {
        var now = 0L; val manager = ConversationManager { now }; research(manager); now = 901_000L
        assertEquals("¿Y Microsoft?", manager.researchQuery("¿Y Microsoft?"))
    }
    @Test fun personalTopicNeverBecomesPublicSearchContext() {
        val manager = ConversationManager(); manager.begin("Mi hermano se llama Juan", 1)
        assertEquals("¿Y Microsoft?", manager.researchQuery("¿Y Microsoft?"))
    }
    @Test fun interruptionKeepsLastUsefulResult() {
        val manager = ConversationManager(); research(manager); manager.interrupt()
        assertEquals("CANCELLED", manager.state.interaction); assertTrue(manager.state.recentResult.isNotBlank())
    }
    @Test fun clearRemovesWorkingMemoryToo() {
        val manager = ConversationManager(); research(manager); manager.clear(); assertEquals(ConversationState(), manager.state)
    }
    @Test fun taskStopsAfterCancellationAndDoesNotClaimCompletion() = runBlocking {
        val executor = LeoTaskExecutor(); val executed = mutableListOf<Int>()
        try {
            executor.run("plan", listOf(1, 2, 3)) { step ->
                executed += step
                if (step == 2) throw CancellationException("stop")
                "dispatched"
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(listOf(1, 2), executed)
        assertEquals(LeoTaskState.CANCELLED, executor.history().single().state)
        assertEquals(LeoTaskState.VERIFYING, executor.history().single().steps.first().state)
    }
    @Test fun dispatchAcknowledgementIsNotPhysicalSuccess() = runBlocking {
        val executor = LeoTaskExecutor(); executor.run("abrir y tocar", listOf(1, 2)) { "API accepted" }
        assertEquals(LeoTaskState.VERIFYING, executor.history().single().state)
    }
    @Test fun failedStepStopsPlan() = runBlocking {
        val executor = LeoTaskExecutor(); var calls = 0
        try { executor.run("plan", listOf(1, 2)) { calls++; error("failure") }; fail() } catch (_: IllegalStateException) { }
        assertEquals(1, calls); assertEquals(LeoTaskState.FAILED, executor.history().single().state)
    }
}
