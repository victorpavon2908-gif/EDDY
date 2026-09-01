package com.eddy.assistant.localai

import com.eddy.assistant.ai.EddyPersonality
import org.junit.Assert.*
import org.junit.Test

class LocalConversationPromptTest {
    @Test fun shrinksContextUsingTokenizerWhileKeepingTheQuestion() {
        val prompt = LocalConversationPrompt.fit("Cómo me llamo", "dato ".repeat(2_000), "fuente ".repeat(2_000), EddyPersonality.WITTY) { it.length }
        assertNotNull(prompt)
        assertTrue(prompt!!.length <= LocalConversationPrompt.MAX_INPUT_TOKENS)
        assertTrue(prompt.contains("Cómo me llamo"))
        assertTrue(LocalConversationPrompt.MAX_INPUT_TOKENS < LocalConversationPrompt.MODEL_TOKENS)
    }

    @Test fun neverInvokesOversizedPromptWhenTokenizationCannotFit() {
        assertNull(LocalConversationPrompt.fit("hola", "", "", EddyPersonality.DIRECT) { 9_000 })
    }
}
