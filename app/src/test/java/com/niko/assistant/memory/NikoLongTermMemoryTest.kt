package com.niko.assistant.memory

import com.niko.assistant.brain.AssistantCommand
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NikoLongTermMemoryTest {
    private lateinit var memory: NikoMemory

    @Before
    fun setUp() {
        memory = NikoMemory(RuntimeEnvironment.getApplication())
        memory.clearAll()
    }

    @After
    fun tearDown() {
        memory.clearAll()
    }

    @Test fun explicitCorrectionRemovesContradictorySemanticAndNoteContext() {
        memory.rememberUserTurn("Recordá que me gusta el café")
        memory.learnExplicitly("Recordá que me gusta el café")
        memory.rememberUserTurn("Ya no tomo café")
        val context = memory.contextForAi(false, "café")
        assertTrue(context.contains("Ya no tomás café", ignoreCase = true))
        assertFalse(context.contains("Te gusta el café", ignoreCase = true))
        assertFalse(context.contains("me gusta el café", ignoreCase = true))
    }

    @Test fun changingCityRetiresOldEpisodeButKeepsCurrentFact() {
        memory.rememberUserTurn("Vivo en Managua")
        memory.rememberUserTurn("Vivo en León")
        val context = memory.contextForAi(false, "dónde vivo")
        assertTrue(context.contains("León"))
        assertFalse(context.contains("Managua"))
    }

    @Test
    fun stablePreferenceIsRecoveredForRelatedQuestion() {
        memory.rememberUserTurn("Me gusta el café bien fuerte.")

        val context = memory.contextForAi(includeDialogue = false, currentMessage = "qué bebida me gusta")

        assertTrue(context.contains("café", ignoreCase = true))
        assertTrue(context.contains("MEMORIA RELEVANTE"))
    }

    @Test
    fun completedActionBecomesProceduralMemory() {
        memory.rememberCompletedCommand(AssistantCommand.SetTorch(true), "Linterna encendida.")

        val context = memory.contextForAi(includeDialogue = false, currentMessage = "cómo solemos manejar la linterna")

        assertTrue(context.contains("PROCEDURAL"))
        assertTrue(context.contains("linterna", ignoreCase = true))
    }

    @Test
    fun likelySecretIsNotPromotedIntoLongTermMemory() {
        memory.rememberUserTurn("mi contraseña es super-secreta-123")

        val context = memory.contextForAi(includeDialogue = false, currentMessage = "contraseña")

        assertFalse(context.contains("super-secreta-123"))
    }
}
