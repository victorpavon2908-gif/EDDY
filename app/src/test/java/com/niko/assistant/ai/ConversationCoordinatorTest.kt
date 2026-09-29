package com.niko.assistant.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ConversationCoordinatorTest {
    @Test fun localFirstDoesNotSendConfidentLocalConversationToCloud() = runBlocking {
        var calls = 0
        val result = ConversationCoordinator.reply("Hola", true, true, false,
            local = { "Aquí estoy." }, cloud = { calls++; null }, fallback = { "fallback" })
        assertEquals("Aquí estoy.", result.text)
        assertEquals(0, calls)
    }

    @Test fun explicitOfflineRequestNeverCallsTheCloudEvenWhenLocalModelIsMissing() = runBlocking {
        var calls = 0
        val result = ConversationCoordinator.reply("Explicame la gravedad sin internet", false, true, true,
            local = { null }, cloud = { calls++; null }, fallback = { "No tengo ese modelo local." })
        assertEquals(0, calls)
        assertEquals("No tengo ese modelo local.", result.text)
    }

    @Test fun uncertaintyTriggersExactlyOneGroundedRequest() = runBlocking {
        var calls = 0
        val result = ConversationCoordinator.reply("Quién inventó el teléfono", true, true, false,
            local = { "No puedo confirmar ese dato." },
            cloud = { requireSources -> calls++; assertTrue(requireSources); NikoAiReply("Verificado.", true, emptyList()) },
            fallback = { "fallback" })
        assertEquals(1, calls)
        assertTrue(result.webUsed)
    }

    @Test fun currentInformationBypassesUnverifiedLocalGeneration() = runBlocking {
        val result = ConversationCoordinator.reply("Qué noticias hay hoy", true, true, false,
            local = { error("Must not invent current news") }, cloud = { assertTrue(it); null }, fallback = { "fallback" })
        assertTrue(result.text.contains("No pude verificar"))
    }

    @Test fun disabledResearchAndPersonalQuestionsDoNotForceSearch() = runBlocking {
        ConversationCoordinator.reply("Cómo estás", false, false, true,
            local = { null }, cloud = { assertFalse(it); null }, fallback = { "Aquí estoy" })
        assertFalse(AutonomousResearch.allowedFor("Hoy me siento triste"))
        assertFalse(AutonomousResearch.allowedFor("No busques noticias"))
        assertTrue(AutonomousResearch.allowedFor("Quién descubrió la penicilina"))
        assertEquals(1, AutonomousResearch.publisherCount(listOf("https://example.com/a", "https://www.example.com/b")))
        assertEquals(0, AutonomousResearch.publisherCount(listOf("https://vertexaisearch.cloud.google.com/grounding-api-redirect/abc")))
    }

    @Test fun explicitSearchWorksWhenAutomaticResearchIsDisabled() = runBlocking {
        var calls = 0
        val result = ConversationCoordinator.reply("Leo, buscame información de Nicaragua", true, false, false,
            local = { error("Explicit search must not use stale local memory") },
            cloud = { requireSources -> calls++; assertTrue(requireSources); NikoAiReply("Fuentes encontradas.", true, emptyList()) },
            fallback = { error("Must search") })
        assertEquals(1, calls)
        assertTrue(result.webUsed)
    }
}
