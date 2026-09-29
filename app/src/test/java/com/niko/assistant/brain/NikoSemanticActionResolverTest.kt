package com.niko.assistant.brain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoSemanticActionResolverTest {
    @Test fun messageFollowUpUsesRecentWhatsAppButExplicitSmsWins() = runBlocking {
        var now = 100L
        val resolver = NikoSemanticActionResolver(LocalBrain(), nowMillis = { now }) { error("No model needed") }
        resolver.resolveMany("Leo, abrime WhatsApp")
        assertEquals(listOf(AssistantCommand.WhatsAppMessage(null, "Voy llegando")),
            resolver.resolveMany("Leo, mandá un mensaje así Voy llegando"))
        assertEquals(listOf(AssistantCommand.ComposeMessage("", "Hola")), resolver.resolveMany("manda un SMS diciendo Hola"))
        resolver.resolveMany("abrí WhatsApp")
        now += 46_000
        assertEquals(listOf(AssistantCommand.ComposeMessage("", "Hola")), resolver.resolveMany("manda un mensaje diciendo Hola"))
    }

    @Test fun leoPrefixCannotMakeNegatedOrderReachTheModel() = runBlocking {
        val resolver = NikoSemanticActionResolver(LocalBrain()) { error("Protected request reached the model") }
        assertTrue(resolver.resolveMany("Leo, no abras WhatsApp").single() is AssistantCommand.Unknown)
    }
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
    fun recentActionResolvesShortFollowUpWithoutExtraModelCall() = runBlocking {
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
        // Esta frase ya la resuelve el parser rápido. El resolver igualmente recuerda
        // la acción, así que el pronombre de seguimiento se resuelve sin encender Qwen.
        assertEquals(listOf(AssistantCommand.SetTorch(true)), resolver.resolveMany("prendeme esa luz del teléfono"))
        now += 2_000L
        assertEquals(listOf(AssistantCommand.SetTorch(false)), resolver.resolveMany("apagála"))
        assertEquals(0, calls)
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
    fun visualQuestionsStayConversationAndNeverExecute() = runBlocking {
        var calls = 0
        val resolver = NikoSemanticActionResolver(LocalBrain()) {
            calls++
            "CAMERA"
        }
        assertTrue(resolver.resolveMany("Niko, mirá esto que aparece en la pantalla") singleIsUnknown true)
        assertTrue(resolver.resolveMany("¿qué dice aquí?") singleIsUnknown true)
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

    @Test
    fun naturalVisibleScreenTaskUsesClosedUiAutomationCommand() = runBlocking {
        val resolver = NikoSemanticActionResolver(LocalBrain()) { "UI_TASK|tocá el botón Continuar" }
        assertEquals(
            listOf(AssistantCommand.AutomateUi("tocá el botón Continuar")),
            resolver.resolveMany("Leo, dale al botón que dice Continuar"),
        )
    }

    @Test
    fun unsafeUiAutomationDslIsRejected() {
        val resolver = NikoSemanticActionResolver(LocalBrain()) { null }
        assertTrue(resolver.parseDsl("UI_TASK|tocá pagar y confirmá la compra").isEmpty())
        assertTrue(resolver.parseDsl("UI_TASK|escribí mi contraseña 1234").isEmpty())
        assertEquals(
            listOf(AssistantCommand.NavigateDevice(DeviceDestination.NOTIFICATIONS)),
            resolver.parseDsl("NAVIGATE|NOTIFICATIONS"),
        )
    }

    private infix fun List<AssistantCommand>.singleIsUnknown(expected: Boolean): Boolean =
        ((size == 1 && first() is AssistantCommand.Unknown) == expected)
}
