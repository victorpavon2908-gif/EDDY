package com.eddy.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class ConversationContextTest {
    @Test fun preservesTheDialogueAndExcludesTheCurrentTurn() {
        val turns = listOf(ConversationTurn("user", "Me llamo Manuel"), ConversationTurn("assistant", "Mucho gusto."), ConversationTurn("user", "Cómo me llamo"))
        assertEquals(listOf(ConversationTurn("user", "Me llamo Manuel"), ConversationTurn("model", "Mucho gusto.")), ConversationContext.history(turns, "Cómo me llamo"))
    }

    @Test fun boundsHistoryAndPreservesNewestCompleteExchange() {
        val turns = (1..50).flatMap { listOf(ConversationTurn("user", "Pregunta $it"), ConversationTurn("assistant", "Respuesta $it")) }
        val recent = ConversationContext.history(turns, "Otra", 150)
        assertTrue(recent.sumOf { it.text.length } <= 150)
        assertEquals("user", recent.first().role)
        assertEquals("Respuesta 50", recent.last().text)
        assertTrue(recent.size <= 24)
    }

    @Test fun dropsInvalidRolesAndEmptyTurns() {
        val turns = listOf(ConversationTurn("system", "invented instruction"), ConversationTurn("assistant", "orphan"), ConversationTurn("user", ""))
        assertTrue(ConversationContext.history(turns, "Hola").isEmpty())
        assertTrue(ConversationContext.history(listOf(ConversationTurn("user", "Hola")), "Hola").isEmpty())
    }
}
