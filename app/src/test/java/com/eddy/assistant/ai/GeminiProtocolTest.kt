package com.eddy.assistant.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeminiProtocolTest {
    @Test fun filtersThoughtPartsAndKeepsAnswer() {
        val response = JSONObject("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"internal"},{"text":"Aquí estoy."}]}}]}""")
        val reply = GeminiProtocol.answer(response)
        assertEquals("Aquí estoy.", reply?.text)
        assertFalse(reply!!.webUsed)
    }
    @Test fun blockedAndEmptyResponsesAreNotAnswers() {
        assertNull(GeminiProtocol.answer(JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}""")))
        assertNull(GeminiProtocol.answer(JSONObject("""{"candidates":[{"finishReason":"SAFETY","content":{"parts":[{"text":"partial"}]}}]}""")))
        assertNull(GeminiProtocol.answer(JSONObject("""{"candidates":[{"content":{"parts":[{"thought":true,"text":"internal"}]}}]}""")))
    }
    @Test fun groundingRequiresActualHttpsSources() {
        val response = JSONObject("""{"candidates":[{"content":{"parts":[{"text":"Respuesta"}]},"groundingMetadata":{"groundingChunks":[{"web":{"uri":"https://example.com/news","title":"Fuente"}},{"web":{"uri":"javascript:alert(1)"}},{"web":{"uri":"https://example.com/news"}}]}}]}""")
        val reply = GeminiProtocol.answer(response)!!
        assertTrue(reply.webUsed)
        assertEquals(1, reply.sources.size)
        assertEquals("Fuente", reply.sources.single().title)
    }
    @Test fun onlyModelAvailabilityErrorsAllowFallback() {
        listOf(0, 400, 401, 403, 429).forEach { assertFalse("code $it", GeminiProtocol.canFallback(it)) }
        listOf(404, 500, 502, 503, 504).forEach { assertTrue(GeminiProtocol.canFallback(it)) }
    }
    @Test fun rejectsSpecializedModelsAndUrlInjection() {
        listOf("gemini-test/image", "gemini-live-test", "gemini-tts", "gemini-x?key=abc", "../models/other").forEach {
            assertFalse(it, GeminiProtocol.isTextModel(it))
        }
        assertTrue(GeminiProtocol.isTextModel("models/gemini-3.7-flash"))
    }
}
