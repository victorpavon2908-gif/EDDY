package com.niko.assistant.ai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroqHttpStreamingTest {
    private class Connection(private val input: InputStream) : HttpURLConnection(URL("https://example.invalid")) {
        @Volatile var disconnected = false
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true; input.close() }
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun getResponseCode() = 200
        override fun getInputStream() = input
    }
    @Test fun realTransportDeliversDecodedDeltasInOrder() = runBlocking {
        val raw = "data: {\"choices\":[{\"delta\":{\"content\":\"Hola\"}}]}\n\n" +
            "data: {\"choices\":[{\"delta\":{\"content\":\" mundo.\"},\"finish_reason\":\"stop\"}]}\n\n" + "data: [DONE]\n\n"
        val connection = Connection(ByteArrayInputStream(raw.toByteArray()))
        val chunks = mutableListOf<String>()
        val result = GroqHttpClient { connection }.stream("test-key", JSONObject(), chunks::add)
        assertTrue(result.completed)
        assertEquals(listOf("Hola", " mundo."), chunks)
        assertTrue(connection.disconnected)
    }
    @Test fun cancellationDisconnectsBlockedNetworkReader() = runBlocking {
        val reading = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val input = object : InputStream() {
            override fun read(): Int { reading.countDown(); closed.await(3, TimeUnit.SECONDS); return -1 }
            override fun close() { closed.countDown() }
        }
        val connection = Connection(input)
        val job = launch { GroqHttpClient { connection }.stream("test-key", JSONObject()) {} }
        withContext(Dispatchers.IO) { assertTrue(reading.await(2, TimeUnit.SECONDS)) }
        withTimeout(1000) { job.cancelAndJoin() }
        assertTrue(connection.disconnected)
        assertEquals(0L, closed.count)
    }
}
