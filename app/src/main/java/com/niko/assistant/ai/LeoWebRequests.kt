package com.niko.assistant.ai

import java.io.Closeable
import java.net.HttpURLConnection
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Network cancellation must not wait for DNS or a socket that ignores Thread.interrupt(). */
internal class LeoWebRequests(workerCount: Int = 8, queueSize: Int = 16) : Closeable {
    private val factory = ThreadFactory { task -> Thread(task, "leo-web-request").apply { isDaemon = true } }
    private val workers = ThreadPoolExecutor(workerCount, workerCount, 30, TimeUnit.SECONDS, ArrayBlockingQueue(queueSize), factory)
    private val closers = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(32), factory)
    private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<CancellableContinuation<*>>()

    suspend fun <T> run(onBusy: () -> Unit = {}, block: (track: (HttpURLConnection) -> Unit) -> T): T? =
        suspendCancellableCoroutine { continuation ->
            pending.add(continuation)
            val active = AtomicReference<HttpURLConnection?>()
            val task = AtomicReference<Future<*>?>()
            fun release() {
                val connection = active.getAndSet(null) ?: return
                // Some platform disconnect implementations also block. Never do that on the caller.
                runCatching { closers.execute { runCatching { connection.disconnect() } } }
            }
            continuation.invokeOnCancellation {
                pending.remove(continuation)
                task.get()?.let { it.cancel(true); if (it is Runnable) workers.remove(it) }
                release()
            }
            val future = runCatching { workers.submit {
                val result = runCatching {
                    if (!continuation.isActive) throw CancellationException("Obsolete web request")
                    block { connection ->
                        active.set(connection)
                        if (!continuation.isActive) { release(); throw CancellationException("Obsolete web request") }
                    }
                }.getOrNull()
                release()
                pending.remove(continuation)
                if (continuation.isActive) continuation.resume(result)
            } }.getOrNull()
            task.set(future)
            if (future == null) {
                pending.remove(continuation)
                onBusy()
                if (continuation.isActive) continuation.resume(null)
            } else if (!continuation.isActive) {
                future.cancel(true)
                if (future is Runnable) workers.remove(future)
                release()
            }
        }

    override fun close() {
        pending.toList().forEach { it.cancel(CancellationException("Web transport closed")) }
        workers.shutdownNow()
        closers.shutdown()
    }
}
