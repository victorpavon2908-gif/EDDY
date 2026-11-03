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
    @Test fun connectionDiagnosticsNeverExposeProviderBodiesOrCredentials() = runBlocking {
        for ((status, expected) in listOf(401 to LeoInferenceClient.Failure.CREDENTIAL,
            403 to LeoInferenceClient.Failure.CREDENTIAL, 404 to LeoInferenceClient.Failure.MODEL,
            429 to LeoInferenceClient.Failure.QUOTA, 503 to LeoInferenceClient.Failure.PROVIDER)) {
            val connection = Connection(status)
            LeoInferenceClient { connection }.use { client ->
                val result = client.request("https://example.com/v1/chat/completions", "model", "private-key", "hola")
                assertNull(result.answer)
                assertEquals(expected, result.failure)
                assertFalse(result.toString().contains("private-key"))
                assertTrue(connection.closed.await(1, TimeUnit.SECONDS))
                assertTrue(connection.disconnected)
            }
        }
    }

    @Test fun onlySuccessfulUncancelledProbeCanReplaceTheProvider() = runBlocking {
        var saves = 0
        val failure = LeoInferenceActivation.activate({ LeoInferenceClient.Result(failure = LeoInferenceClient.Failure.QUOTA) }, { saves++ })
        assertNull(failure.answer)
        assertEquals(0, saves)
        val wrong = LeoInferenceActivation.activate({ LeoInferenceClient.Result(answer = "No puedo responder") }, { saves++ })
        assertNull(wrong.answer)
        assertEquals(0, saves)
        val success = LeoInferenceActivation.activate({ LeoInferenceClient.Result(answer = "LEO conectado") }, { saves++ })
        assertNotNull(success.answer)
        assertEquals(1, saves)
        val job = launch {
            LeoInferenceActivation.activate({ delay(10_000); LeoInferenceClient.Result(answer = "tarde") }, { saves++ })
        }
        yield(); job.cancelAndJoin()
        assertEquals(1, saves)
    }
    private open class Connection(private val status: Int = 200) : HttpURLConnection(URL("https://example.com/v1/chat/completions")) {
        val payload = ByteArrayOutputStream()
        @Volatile var disconnected = false
        val closed = CountDownLatch(1)
        override fun connect() {}
        override fun disconnect() { disconnected = true; closed.countDown() }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getOutputStream() = payload
        override fun getInputStream() = ByteArrayInputStream("""{"choices":[{"finish_reason":"stop","message":{"content":"fixture answer"}}]}""".toByteArray())
    }
    @Test fun deadlineDisconnectsAStalledProviderAndAllowsANewRequest() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val connection = object : Connection() {
            override fun getResponseCode(): Int { entered.countDown(); released.await(5, TimeUnit.SECONDS); return 200 }
            override fun disconnect() { super.disconnect(); released.countDown() }
        }
        LeoInferenceClient { connection }.use { client ->
            val answer = withTimeout(3000) {
                client.reply("https://example.com/v1/chat/completions", "model", "", "explica variables", timeoutMs = 500)
            }
            assertNull(answer)
            assertEquals(0L, entered.count)
            assertTrue(connection.closed.await(1, TimeUnit.SECONDS))
                assertTrue(connection.disconnected)
            assertEquals("fixture answer", client.reply("https://example.com/v1/chat/completions", "model", "", "explica variables"))
        }
    }

    @Test fun sendsPostWithoutFollowingRedirects() = runBlocking {
        val connection = Connection()
        LeoInferenceClient { connection }.use { client ->
            assertEquals("fixture answer", client.reply("https://example.com/v1/chat/completions", "model", "secret", "explica variables"))
            assertEquals("POST", connection.requestMethod)
            assertFalse(connection.instanceFollowRedirects)
            assertTrue(connection.closed.await(1, TimeUnit.SECONDS))
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
            assertTrue(connection.closed.await(1, TimeUnit.SECONDS))
                assertTrue(connection.disconnected)
        }
    }
}
