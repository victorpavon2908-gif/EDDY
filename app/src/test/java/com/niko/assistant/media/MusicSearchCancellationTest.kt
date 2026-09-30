package com.niko.assistant.media

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MusicSearchCancellationTest {
    @Test fun cancellationClosesBlockedConnectionPromptly() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val disconnected = CountDownLatch(1)
        val connection = object : HttpURLConnection(URL("https://itunes.apple.com/search")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { disconnected.countDown() }
            override fun getResponseCode(): Int {
                entered.complete(Unit)
                disconnected.await(3, TimeUnit.SECONDS)
                throw java.io.IOException("closed")
            }
        }
        val request = launch { MusicSearch.fetch(connection) }
        try {
            withTimeout(2000) { entered.await() }
            withTimeout(2000) { request.cancelAndJoin() }
            assertEquals(0L, disconnected.count)
        } finally { request.cancel(); connection.disconnect() }
    }
}
