package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class GroqConversationTest {
    @Test fun mapsExistingModelHistoryToAssistantWithoutDuplicatingCurrentTurn() {
        val history = listOf(ConversationTurn("user", "Me llamo Manuel"), ConversationTurn("model", "Lo recordaré"), ConversationTurn("user", "Qué recordás"))
        val payload = GroqConversation.payload("Qué recordás", "te gusta el café", history, false)
        val messages = payload.getJSONArray("messages")
        assertEquals(4, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertTrue(messages.getJSONObject(0).getString("content").contains("te gusta el café"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("Qué recordás", messages.getJSONObject(3).getString("content"))
        assertFalse(payload.has("contents"))
    }

    @Test fun searchRestrictsCompoundToWebAndRequestsCitations() {
        val base = GroqConversation.payload("Qué noticias hay", "tono acústico suave", emptyList(), true, NikoPersonality.DIRECT)
        val payload = GroqConversation.forModel(base, GroqProtocol.SEARCH_MODEL, true)
        assertEquals("groq/compound", payload.getString("model"))
        assertEquals("[\"web_search\"]", payload.getJSONObject("compound_custom").getJSONObject("tools").getJSONArray("enabled_tools").toString())
        assertEquals("enabled", payload.getString("citation_options"))
        assertEquals(1_200, payload.getInt("max_completion_tokens"))
        val system = payload.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system.contains(NikoPersonality.DIRECT.guidance()))
        assertTrue(system.contains("fuentes independientes"))
        assertTrue(system.contains("detalles útiles"))
        assertTrue(system.contains("no uses Markdown"))
        assertFalse(base.has("compound_custom"))
    }

    @Test fun ordinaryChatDisablesToolsAndUsesShortVoiceBudget() {
        val payload = GroqConversation.forModel(GroqConversation.payload("Hola", "", emptyList(), false), GroqProtocol.DEFAULT_MODEL, false)
        assertEquals("none", payload.getString("tool_choice"))
        assertFalse(payload.has("compound_custom"))
        assertFalse(payload.has("generationConfig"))
        assertFalse(payload.has("google_search"))
        assertEquals(512, payload.getInt("max_completion_tokens"))
        assertFalse(payload.getBoolean("stream"))
        val system = payload.getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system.contains("texto limpio"))
    }
}
