package com.niko.assistant.localai

import com.niko.assistant.ai.NikoPersonality
import org.junit.Assert.*
import org.junit.Test

class LocalConversationPromptTest {
    @Test fun shrinksContextUsingTokenizerWhileKeepingTheQuestion() {
        // Aproximación deliberada de tokenizer: el presupuesto es de tokens, no de
        // caracteres. El prompt visual puede superar 960 caracteres y seguir entrando
        // cómodamente en el KV de 1280 tokens de Qwen.
        val tokenizer: (String) -> Int = { text -> (text.length + 3) / 4 }
        val prompt = LocalConversationPrompt.fit(
            "Cómo me llamo",
            "dato ".repeat(2_000),
            "fuente ".repeat(2_000),
            NikoPersonality.WITTY,
            tokenizer,
        )
        assertNotNull(prompt)
        assertTrue(tokenizer(prompt!!) <= LocalConversationPrompt.MAX_INPUT_TOKENS)
        assertTrue(prompt.contains("Cómo me llamo"))
        assertTrue(LocalConversationPrompt.MAX_INPUT_TOKENS < LocalConversationPrompt.MODEL_TOKENS)
    }

    @Test fun neverInvokesOversizedPromptWhenTokenizationCannotFit() {
        assertNull(LocalConversationPrompt.fit("hola", "", "", NikoPersonality.DIRECT) { 9_000 })
    }
}
