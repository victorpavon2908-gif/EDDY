package com.niko.assistant.inference

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Protocol transport fixtures only. These are not model quality or live provider measurements. */
class LeoInferenceClientTest {
    private open class Connection(private val status: Int = 200) : HttpURLConnection(URL("https://example.com/v1/chat/completions")) {
        val payload = ByteArrayOutputStream()
        @Volatile var disconnected = false
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getOutputStream() = payload
        override fun getInputStream() = ByteArrayInputStream("""{"choices":[{"finish_reason":"stop","message":{"content":"fixture answer"}}]}""".toByteArray())
    }
    @Test fun sendsPostWithoutFollowingRedirects() = runBlocking {
        val connection = Connection()
        LeoInferenceClient { connection }.use { client ->
            assertEquals("fixture answer", client.reply("https://example.com/v1/chat/completions", "model", "secret", "explica variables"))
            assertEquals("POST", connection.requestMethod)
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.disconnected)
        }
    }
    @Test fun failureIsLocalFallbackNotAnExceptionOrProviderErrorLeak() = runBlocking {
        LeoInferenceClient { Connection(401) }.use { client ->
            assertNull(client.reply("https://example.com/v1/chat/completions", "model", "wrong", "explica variables"))
        }
    }
    @Test fun cancellationDisconnectsInFlightRequest() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val connection = object : Connection() {
            override fun getResponseCode(): Int { entered.countDown(); released.await(5, TimeUnit.SECONDS); return 200 }
            override fun disconnect() { super.disconnect(); released.countDown() }
        }
        LeoInferenceClient { connection }.use { client ->
            val job = launch(Dispatchers.Default) { client.reply("https://example.com/v1/chat/completions", "model", "", "explica variables") }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(connection.disconnected)
        }
    }
}
