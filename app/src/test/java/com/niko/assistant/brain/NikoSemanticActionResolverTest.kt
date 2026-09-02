package com.niko.assistant.brain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoSemanticActionResolverTest {
    @Test
    fun freeWordingCanOpenAnyNamedApp() = runBlocking {
        val resolver = NikoSemanticActionResolver(LocalBrain()) { "OPEN_APP|YouTube" }
        val commands = resolver.resolveMany("haceme el favor de entrar a YouTube cuando podás")
        assertEquals(listOf(AssistantCommand.OpenAppByName("YouTube")), commands)
    }

    @Test
    fun politeNaturalTorchRequestMapsToAction() = runBlocking {
        val resolver = NikoSemanticActionResolver(LocalBrain()) { "TORCH|ON" }
        val commands = resolver.resolveMany("serías tan amable de prenderme la linterna por favor")
        assertEquals(listOf(AssistantCommand.SetTorch(true)), commands)
    }

    @Test
    fun recentActionResolvesShortFollowUpWithoutCallingModelAgain() = runBlocking {
        var calls = 0
        var now = 1_000L
        val resolver = NikoSemanticActionResolver(
            brain = LocalBrain(),
            structuredCompletion = {
                calls++
                "TORCH|ON"
            },
            nowMillis = { now },
        )
        assertEquals(listOf(AssistantCommand.SetTorch(true)), resolver.resolveMany("prendeme esa luz del teléfono"))
        now += 2_000L
        assertEquals(listOf(AssistantCommand.SetTorch(false)), resolver.resolveMany("apagála"))
        assertEquals(1, calls)
    }

    @Test
    fun negationAndHowToQuestionsNeverReachExecutionModel() = runBlocking {
        var calls = 0
        val resolver = NikoSemanticActionResolver(LocalBrain()) {
            calls++
            "OPEN_APP|YouTube"
        }
        assertTrue(resolver.resolveMany("no abras YouTube") singleIsUnknown true)
        assertTrue(resolver.resolveMany("cómo puedo apagar la linterna") singleIsUnknown true)
        assertEquals(0, calls)
    }

    @Test
    fun malformedOrUnsafeDslIsRejected() {
        val resolver = NikoSemanticActionResolver(LocalBrain()) { null }
        assertTrue(resolver.parseDsl("DELETE_FILE|/data/user/0").isEmpty())
        assertTrue(resolver.parseDsl("BRIGHTNESS|900").isEmpty())
        assertTrue(resolver.parseDsl("ALARM|31|99|x").isEmpty())
    }

    @Test
    fun compoundSafeDslCreatesMultipleActions() {
        val resolver = NikoSemanticActionResolver(LocalBrain()) { null }
        val commands = resolver.parseDsl("OPEN_APP|YouTube\nTORCH|ON")
        assertEquals(
            listOf(AssistantCommand.OpenAppByName("YouTube"), AssistantCommand.SetTorch(true)),
            commands,
        )
    }

    private infix fun List<AssistantCommand>.singleIsUnknown(expected: Boolean): Boolean =
        ((size == 1 && first() is AssistantCommand.Unknown) == expected)
}
