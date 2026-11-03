package com.niko.assistant.ai

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LeoWebRequestsTest {
    private fun awaitIgnoringInterrupts(latch: CountDownLatch) {
        while (latch.count > 0) try { latch.await(50, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { }
    }
    @Test fun timeoutReturnsEvenWhenSocketAndDisconnectIgnoreInterruption() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val closed = CountDownLatch(1)
        val connection = object : HttpURLConnection(URL("https://example.org")) {
            override fun connect() {}
            override fun usingProxy() = false
            override fun disconnect() { closed.countDown(); awaitIgnoringInterrupts(released) }
        }
        LeoWebRequests(workerCount = 1, queueSize = 1).use { requests ->
            try {
                val start = System.nanoTime()
                val result = withTimeoutOrNull(500) {
                    requests.run { track -> track(connection); entered.countDown(); awaitIgnoringInterrupts(released); "obsolete" }
                }
                assertNull(result)
                assertEquals(0L, entered.count)
                assertTrue("Cancellation waited for the socket", (System.nanoTime() - start) / 1_000_000 < 1500)
                assertTrue(closed.await(1, TimeUnit.SECONDS))
            } finally { released.countDown() }
        }
    }
    @Test fun repeatedCancelledQueuedRequestsDoNotExhaustTheQueue() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        LeoWebRequests(workerCount = 1, queueSize = 1).use { requests ->
            val running = async(Dispatchers.Default) { requests.run { entered.countDown(); awaitIgnoringInterrupts(released); "first" } }
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                repeat(4) {
                    var busy = false
                    assertNull(withTimeoutOrNull(50) { requests.run(onBusy = { busy = true }) { "queued" } })
                    assertFalse("Cancelled requests stayed in the queue", busy)
                }
                released.countDown()
                assertEquals("first", running.await())
                assertEquals("new", requests.run { "new" })
            } finally { released.countDown(); running.cancelAndJoin() }
        }
    }
    @Test fun lateResultCannotReplaceANewerRequestAndSaturationIsBounded() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        LeoWebRequests(workerCount = 1, queueSize = 1).use { requests ->
            val running = async(Dispatchers.Default) { requests.run { entered.countDown(); awaitIgnoringInterrupts(released); "old" } }
            try {
                assertTrue(entered.await(1, TimeUnit.SECONDS))
                val queued = async(start = CoroutineStart.UNDISPATCHED) { requests.run { "queued" } }
                var busy = false
                assertNull(requests.run(onBusy = { busy = true }) { "never" })
                assertTrue(busy)
                running.cancelAndJoin()
                assertTrue(running.isCancelled)
                released.countDown()
                assertEquals("queued", queued.await())
            } finally { released.countDown(); running.cancelAndJoin() }
        }
    }
}
