package com.niko.assistant.ai

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GroqStreamingTest {
    private fun payload() = GroqConversation.payload("Hola", "", emptyList(), false)
    private fun gateway(budget: Long = 1000) = GroqGateway(budget, GroqTransport { _, _ -> error("No full-response request") })

    @Test fun publishesFirstWordsBeforeRequestFinishes() = runBlocking {
        val first = CompletableDeferred<String>()
        val finish = CompletableDeferred<Unit>()
        val stream = GroqStreamTransport { _, request, emit ->
            assertTrue(request.getBoolean("stream"))
            assertEquals("none", request.getString("tool_choice"))
            emit("Hola. ")
            finish.await()
            emit("Aquí estoy.")
            GroqStreamResult(200, completed = true)
        }
        val job = async { gateway().executeStreaming(payload(), "key", GroqProtocol.DEFAULT_MODEL, stream) { first.complete(it) } }
        assertEquals("Hola. ", first.await())
        assertFalse(job.isCompleted)
        finish.complete(Unit)
        assertEquals("Hola. Aquí estoy.", job.await()?.text)
    }
    @Test fun partialFailureDoesNotRepeatOrSwitchModels() = runBlocking {
        var calls = 0
        val heard = StringBuilder()
        val stream = GroqStreamTransport { _, _, emit -> calls++; emit("Respuesta parcial."); GroqStreamResult(503) }
        val answer = gateway().executeStreaming(payload(), "key", GroqProtocol.DEFAULT_MODEL, stream) { heard.append(it) }
        assertEquals(1, calls)
        assertEquals(answer?.text, heard.toString())
        assertTrue(heard.contains("se interrumpió"))
    }
    @Test fun cancellationStopsProducerWithoutFallbackNotice() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var released = false
        val heard = mutableListOf<String>()
        val stream = GroqStreamTransport { _, _, emit ->
            try { emit("Hola."); started.complete(Unit); awaitCancellation() }
            finally { released = true }
        }
        val job = launch { gateway().executeStreaming(payload(), "key", GroqProtocol.DEFAULT_MODEL, stream, heard::add) }
        started.await(); job.cancelAndJoin()
        assertTrue(released)
        assertEquals(listOf("Hola."), heard)
    }
    @Test fun unavailableModelFallsBackOnlyBeforeFirstText() = runBlocking {
        var calls = 0
        val stream = GroqStreamTransport { _, _, emit ->
            if (++calls == 1) GroqStreamResult(404) else { emit("Listo."); GroqStreamResult(200, completed = true) }
        }
        assertEquals("Listo.", gateway().executeStreaming(payload(), "key", "retired", stream) {}?.text)
        assertEquals(2, calls)
    }
    @Test fun authenticationFailureNeverRetries() = runBlocking {
        var calls = 0
        val stream = GroqStreamTransport { _, _, _ -> calls++; GroqStreamResult(401) }
        assertNull(gateway().executeStreaming(payload(), "key", GroqProtocol.DEFAULT_MODEL, stream) {})
        assertEquals(1, calls)
    }
    @Test fun stalledPartialResponseHasOneDeadlineAndOneNotice() = runBlocking {
        val heard = StringBuilder()
        val stream = GroqStreamTransport { _, _, emit -> emit("Hola."); awaitCancellation() }
        val answer = gateway(30).executeStreaming(payload(), "key", GroqProtocol.DEFAULT_MODEL, stream) { heard.append(it) }
        assertEquals(answer?.text, heard.toString())
        assertTrue(heard.contains("se interrumpió"))
    }
}
