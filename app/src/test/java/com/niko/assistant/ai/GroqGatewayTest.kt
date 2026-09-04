package com.niko.assistant.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GroqGatewayTest {
    private val ok = GroqHttpResult(200, """{"choices":[{"finish_reason":"stop","message":{"content":"Aquí estoy."}}]}""")
    private fun payload(web: Boolean = false) = GroqConversation.payload("Hola", "", emptyList(), web)

    @Test fun quotaOrInvalidKeyNeverTriggersAnotherModel() = runBlocking {
        for (code in listOf(401, 403, 429)) {
            var calls = 0
            val gateway = GroqGateway { _, _ -> calls++; GroqHttpResult(code, "") }
            assertNull(gateway.execute(payload(), "test_key", GroqProtocol.DEFAULT_MODEL, false))
            assertEquals(1, calls)
            assertNotNull(gateway.lastError)
        }
    }

    @Test fun retiredChatModelFallsBackWithinGroqAndClearsPreviousError() = runBlocking {
        val used = mutableListOf<String>()
        val gateway = GroqGateway { _, request ->
            used.add(request.getString("model"))
            if (used.size == 1) GroqHttpResult(400, """{"error":{"code":"model_decommissioned"}}""") else ok
        }
        assertNotNull(gateway.execute(payload(), "test_key", "llama-retired", false))
        assertEquals(listOf("llama-retired", GroqProtocol.DEFAULT_MODEL), used)
        assertNull(gateway.lastError)
        assertEquals(GroqProtocol.DEFAULT_MODEL, gateway.lastModelUsed)
    }

    @Test fun modelPermission403FallsBackButInvalidKey403DoesNot() = runBlocking {
        val used = mutableListOf<String>()
        val gateway = GroqGateway { _, request ->
            used.add(request.getString("model"))
            if (used.size == 1) GroqHttpResult(403, """{"error":{"code":"permission_denied","message":"model access is restricted for this project"}}""") else ok
        }
        assertNotNull(gateway.execute(payload(), "test_key", GroqProtocol.QUALITY_MODEL, false))
        assertEquals(listOf(GroqProtocol.QUALITY_MODEL, GroqProtocol.DEFAULT_MODEL), used)

        var calls = 0
        val invalid = GroqGateway { _, _ ->
            calls++
            GroqHttpResult(403, """{"error":{"code":"invalid_api_key","message":"invalid API key"}}""")
        }
        assertNull(invalid.execute(payload(), "test_key", GroqProtocol.QUALITY_MODEL, false))
        assertEquals(1, calls)
    }

    @Test fun unavailableSearchOnlyTriesSearchCapableModelsFastestFirst() = runBlocking {
        val used = mutableListOf<String>()
        val gateway = GroqGateway { _, request -> used.add(request.getString("model")); GroqHttpResult(503, "") }
        assertNull(gateway.execute(payload(true), "test_key", GroqProtocol.DEFAULT_MODEL, true))
        assertEquals(listOf(GroqProtocol.FAST_SEARCH_MODEL, GroqProtocol.SEARCH_MODEL), used)
    }

    @Test fun emptyOrFilteredResponseIsNotRetried() = runBlocking {
        var calls = 0
        val gateway = GroqGateway { _, _ -> calls++; GroqHttpResult(200, """{"choices":[{"finish_reason":"content_filter","message":{"content":"partial"}}]}""") }
        assertNull(gateway.execute(payload(), "test_key", GroqProtocol.DEFAULT_MODEL, false))
        assertEquals(1, calls)
    }

    @Test fun rejectsMissingKeyAndWrongProviderBeforeNetwork() = runBlocking {
        val gateway = GroqGateway { _, _ -> fail("Must not contact network"); ok }
        assertNull(gateway.execute(payload(), "", GroqProtocol.DEFAULT_MODEL, false))
        assertNull(gateway.execute(payload(), "bad\nkey", GroqProtocol.DEFAULT_MODEL, false))
        assertNull(gateway.execute(payload(), "test_key", "gemini-3.7-flash", false))
    }

    @Test fun totalDeadlineCancelsPendingRequest() = runBlocking {
        var cancelled = false
        val gateway = GroqGateway(budgetMs = 80) { _, _ ->
            try { delay(10_000); ok } finally { cancelled = true }
        }
        assertNull(gateway.execute(payload(), "test_key", GroqProtocol.DEFAULT_MODEL, false))
        assertTrue(cancelled)
        assertTrue(gateway.lastError!!.contains("tardó demasiado"))
    }

    @Test fun serviceCancellationPropagatesToTransport() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var cancelled = false
        val gateway = GroqGateway { _, _ ->
            entered.complete(Unit)
            try { delay(10_000); ok } finally { cancelled = true }
        }
        val job = launch { gateway.execute(payload(), "test_key", GroqProtocol.DEFAULT_MODEL, false) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(cancelled)
    }
}
