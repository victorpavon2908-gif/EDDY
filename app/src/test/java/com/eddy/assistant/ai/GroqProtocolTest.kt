package com.eddy.assistant.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroqProtocolTest {
    @Test fun readsOnlyFinalContentAndDoesNotInventSearchFromProse() {
        val json = JSONObject("""{"choices":[{"finish_reason":"stop","message":{"content":"Mirá https://example.com","reasoning":"No pronunciar razonamiento"}}]}""")
        val reply = GroqProtocol.answer(json)!!
        assertEquals("Mirá https://example.com", reply.text)
        assertFalse(reply.webUsed)
        assertTrue(reply.sources.isEmpty())
    }

    @Test fun rejectsNullContentAndToolRequestsWithoutRetryingAsAnswers() {
        listOf("content_filter", "tool_calls", "function_call").forEach { reason ->
            assertNull(GroqProtocol.answer(JSONObject("""{"choices":[{"finish_reason":"$reason","message":{"content":"partial"}}]}""")))
        }
        assertNull(GroqProtocol.answer(JSONObject("""{"choices":[{"message":{"content":null,"reasoning":"internal"}}]}""")))
        assertNull(GroqProtocol.answer(JSONObject("""{"choices":[]}""")))
    }

    @Test fun usesDocumentedSearchResultsAcrossToolsAndFiltersUnsafeOrDuplicateLinks() {
        val json = JSONObject("""{"choices":[{"finish_reason":"stop","message":{"content":"Respuesta actual.","executed_tools":[
          {"type":"search","search_results":{"results":[{"title":"Fuente","url":"https://example.com/a"},{"url":"https://example.com/a"},{"url":"javascript:alert(1)"},{"url":"https://user@example.com/private"}]}},
          {"type":"web_search","search_results":[{"title":"Otra","url":"https://example.org/b"}]},
          {"type":"code_interpreter","search_results":{"results":[{"url":"https://inventada.example"}]}}
        ]}}]}""")
        val reply = GroqProtocol.answer(json)!!
        assertTrue(reply.webUsed)
        assertEquals(listOf("https://example.com/a", "https://example.org/b"), reply.sources.map { it.url })
    }

    @Test fun searchExecutionWithoutSourceMetadataRemainsUnverified() {
        val json = JSONObject("""{"choices":[{"message":{"content":"Una afirmación.","executed_tools":[{"type":"search","output":"Un resultado sin enlaces verificables"}]}}]}""")
        assertFalse(GroqProtocol.answer(json)!!.webUsed)
    }

    @Test fun fallbackOnlyRepairsUnavailableModelsOrTransientServerErrors() {
        listOf(0, 400, 401, 403, 413, 429).forEach { assertFalse(GroqProtocol.canFallback(it, "{}")) }
        listOf(404, 500, 502, 503, 504).forEach { assertTrue(GroqProtocol.canFallback(it, "{}")) }
        assertTrue(GroqProtocol.canFallback(400, """{"error":{"code":"model_decommissioned"}}"""))
        assertFalse(GroqProtocol.canFallback(400, """{"error":{"code":"invalid_api_key"}}"""))
    }

    @Test fun acceptsChatIdentifiersButCannotConfigureOtherProvidersOrToolsAsChat() {
        listOf(GroqProtocol.DEFAULT_MODEL, GroqProtocol.FAST_MODEL, "openai/gpt-oss-120b").forEach { assertTrue(it, GroqProtocol.isChatModel(it)) }
        listOf("gemini-3.7-flash", "whisper-large-v3", "groq/compound", "../model", "model?key=x", "https://other/model", "a\nb").forEach { assertFalse(it, GroqProtocol.isChatModel(it)) }
        assertTrue(GroqProtocol.isSearchModel("groq/compound"))
    }
}
