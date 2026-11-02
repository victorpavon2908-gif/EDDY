package com.niko.assistant.agent

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

/** A shared deadline. Producers leave time for fallback, persistence and a spoken reply. */
class LeoTurnBudget(val totalMs: Long, private val nanoTime: () -> Long = System::nanoTime) : AbstractCoroutineContextElement(Key) {
    init { require(totalMs > 0) }
    private val deadline = nanoTime() + totalMs * 1_000_000L
    fun cap(maxMs: Long, reserveMs: Long = 3_000L): Long =
        minOf(maxMs, (deadline - nanoTime()) / 1_000_000L - reserveMs).coerceAtLeast(0L)
    companion object Key : CoroutineContext.Key<LeoTurnBudget> {
        suspend fun limit(maxMs: Long, reserveMs: Long = 3_000L): Long =
            currentCoroutineContext()[Key]?.cap(maxMs, reserveMs) ?: maxMs
    }
}
