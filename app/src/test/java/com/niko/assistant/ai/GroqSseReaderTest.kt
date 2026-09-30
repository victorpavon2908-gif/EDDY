package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class GroqSseReaderTest {
    private fun event(content: String) = "data: $content\n\n"
    private val done = event("""{"choices":[{"delta":{},"finish_reason":"stop"}]}""") + event("[DONE]")
    @Test fun emitsOnlyPublicContentAndIgnoresReasoningAndUsage() {
        val input = ": keepalive\n\n" + event("""{"choices":[{"delta":{"reasoning":"private","role":"assistant"}}]}""") +
            event("""{"choices":[{"delta":{"content":"Hola, "}}]}""") +
            event("""{"choices":[{"delta":{"content":"¿cómo estás?"}}]}""") + event("""{"choices":[],"usage":{}}""") + done
        val chunks = mutableListOf<String>()
        assertTrue(GroqSseReader.read(StringReader(input), chunks::add))
        assertEquals(listOf("Hola, ", "¿cómo estás?"), chunks)
    }
    @Test fun eofWithoutDoneIsNotSuccess() {
        assertFalse(GroqSseReader.read(StringReader(event("""{"choices":[{"delta":{"content":"parcial"}}]}"""))) {})
    }
    @Test fun acceptsCrlfAndMultilineData() {
        val input = "data: {\r\ndata: \"choices\":[{\"delta\":{\"content\":\"Hola\"}}]}\r\n\r\n" + done
        val chunks = mutableListOf<String>()
        assertTrue(GroqSseReader.read(StringReader(input), chunks::add))
        assertEquals(listOf("Hola"), chunks)
    }
    @Test(expected = IllegalStateException::class) fun rejectsFilteredFinishBeforePublishingItsDelta() {
        GroqSseReader.read(StringReader(event("""{"choices":[{"finish_reason":"content_filter","delta":{"content":"ignored"}}]}"""))) { fail("Filtered delta") }
    }
    @Test(expected = IllegalStateException::class) fun rejectsOversizedLine() {
        GroqSseReader.read(StringReader("data: " + "x".repeat(65_537))) {}
    }
    @Test(expected = IllegalStateException::class) fun rejectsDoneWithoutFinish() {
        GroqSseReader.read(StringReader(event("[DONE]"))) {}
    }
}
