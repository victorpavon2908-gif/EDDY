package com.niko.assistant.inference

import com.niko.assistant.ai.ConversationTurn
import com.niko.assistant.modes.LeoMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking

class LeoGoogleProtocolTest {
    @Test fun nativeClientRetriesOneTemporaryFailureButNeverRetriesAQuotaRefusal() = runBlocking {
        for (firstStatus in listOf(503, 429)) {
            var attempts = 0
            LeoInferenceClient {
                val status = if (++attempts == 1) firstStatus else 200
                object : java.net.HttpURLConnection(java.net.URL("https://fixture.example")) {
                    override fun connect() {}
                    override fun disconnect() {}
                    override fun usingProxy() = false
                    override fun getResponseCode() = status
                    override fun getOutputStream() = java.io.ByteArrayOutputStream()
                    override fun getInputStream() = java.io.ByteArrayInputStream(
                        """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"LEO conectado"}]}}]}""".toByteArray())
                }
            }.use { client ->
                val result = client.request(LeoGoogleProvider.ENDPOINT, LeoGoogleProvider.MODEL, "fixture-key", LeoInferenceActivation.PROBE)
                if (firstStatus == 503) { assertEquals(2, attempts); assertEquals("LEO conectado", result.answer) }
                else { assertEquals(1, attempts); assertEquals(LeoInferenceClient.Failure.QUOTA, result.failure) }
            }
        }
    }
    @Test fun profileThinkingMatchesTheActualModelCapabilities() {
        val fast = JSONObject(LeoGoogleProtocol.payload(LeoGoogleProvider.MODEL, "Hola", null, emptyList(), LeoMode.SUPER))
        assertEquals("minimal", fast.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
        val flash = JSONObject(LeoGoogleProtocol.payload(LeoGoogleProvider.FLASH, "Hola", null, emptyList(), LeoMode.SUPER))
        assertEquals("low", flash.getJSONObject("generationConfig").getJSONObject("thinkingConfig").getString("thinkingLevel"))
    }
    @Test fun nativeRolesKeepHistorySeparateFromUntrustedResearch() {
        val history = listOf(ConversationTurn("user", "Referencia"), ConversationTurn("assistant", "Respuesta"))
        val chat = JSONObject(LeoGoogleProtocol.payload(LeoGoogleProvider.MODEL, "Continuá", null, history, LeoMode.NORMAL))
        assertEquals("model", chat.getJSONArray("contents").getJSONObject(1).getString("role"))
        val evidence = "[{\"source\":1,\"text\":\"Un extracto público de la página\"}]"
        val research = JSONObject(LeoGoogleProtocol.payload(LeoGoogleProvider.MODEL, "Resumen", evidence, history, LeoMode.NORMAL))
        assertEquals(1, research.getJSONArray("contents").length())
        assertTrue(research.toString().contains("untrusted_evidence"))
        assertFalse(research.toString().contains("Referencia"))
    }
    @Test fun rejectsTruncationToolCallsAndPrivateThoughts() {
        val valid = """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"private"},{"text":"LEO conectado"}]}}]}"""
        assertEquals("LEO conectado", LeoGoogleProtocol.answer(valid))
        assertTrue(runCatching { LeoGoogleProtocol.answer(valid.replace("STOP", "MAX_TOKENS")) }.isFailure)
        assertTrue(runCatching { LeoGoogleProtocol.answer(valid.replace("\"text\":\"LEO conectado\"", "\"functionCall\":{}")) }.isFailure)
        assertTrue(runCatching { LeoGoogleProtocol.endpoint("../bad?key=secret") }.isFailure)
    }
    @Test fun actualClientUsesNativeEndpointAndHeaderWithoutEmbeddingCredentialInUrl() = runBlocking {
        var target = ""
        val connection = object : java.net.HttpURLConnection(java.net.URL("https://fixture.example")) {
            override fun connect() {}
            override fun disconnect() {}
            override fun usingProxy() = false
            override fun getResponseCode() = 200
            override fun getOutputStream() = java.io.ByteArrayOutputStream()
            override fun getInputStream() = java.io.ByteArrayInputStream(
                """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"LEO conectado"}]}}]}""".toByteArray())
        }
        LeoInferenceClient { uri -> target = uri.toString(); connection }.use { client ->
            assertEquals("LEO conectado", client.reply(LeoGoogleProvider.ENDPOINT, LeoGoogleProvider.MODEL, "fixture-key", LeoInferenceActivation.PROBE))
            assertTrue(target.endsWith(":generateContent"))
            assertFalse(target.contains("fixture-key"))
            assertEquals("fixture-key", connection.getRequestProperty("x-goog-api-key"))
            assertNull(connection.getRequestProperty("Authorization"))
        }
    }
}
