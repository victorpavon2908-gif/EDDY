package com.eddy.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class GeminiConversationTest {
    @Test fun tunesVoiceLatencyOnlyForSupportedModelsWithoutMutatingFallbackPayload() {
        val base = GeminiConversation.payload("Hola", "", emptyList(), true)
        val fast = GeminiConversation.forModel(base, "models/gemini-3.7-flash")
        assertEquals("low", fast.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
        assertTrue(fast.has("tools"))
        assertFalse(base.getJSONObject("generationConfig").has("thinkingConfig"))
        assertFalse(GeminiConversation.forModel(base, "gemini-2.5-flash").getJSONObject("generationConfig").has("thinkingConfig"))
    }

    @Test fun sendsRealHistoryRolesWithoutDuplicatingCurrentQuestion() {
        val history = listOf(ConversationTurn("user", "Me llamo Manuel"), ConversationTurn("assistant", "Hola Manuel"), ConversationTurn("user", "Qué recordás"))
        val payload = GeminiConversation.payload("Qué recordás", "te gusta el café", history, false)
        val contents = payload.getJSONArray("contents")
        assertEquals(3, contents.length())
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals("Qué recordás", contents.getJSONObject(2).getJSONArray("parts").getJSONObject(0).getString("text"))
        assertFalse(payload.has("tools"))
        val system = payload.getJSONObject("system_instruction").toString()
        assertTrue(system.contains("te gusta el café"))
        assertFalse(system.contains("Hola Manuel"))
    }

    @Test fun searchesOnlyWhenRequestedAndKeepsMemoryWithLongDialogue() {
        val history = (1..30).map { ConversationTurn(if (it % 2 == 1) "user" else "assistant", "x".repeat(2_000)) }
        val payload = GeminiConversation.payload("Qué noticias hay", "nombre: Manuel; tono acústico: suave", history, true)
        assertTrue(payload.getJSONArray("tools").getJSONObject(0).has("google_search"))
        assertTrue(payload.getJSONObject("system_instruction").toString().contains("tono acústico: suave"))
        assertTrue(payload.toString().length < 20_000)
    }
}
