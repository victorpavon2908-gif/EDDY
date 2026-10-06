package com.niko.assistant.agent

import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A capability for one turn. Cancellation invalidates callbacks even if a producer ignores Job. */
class TurnController {
    class Token internal constructor(val generation: Long, internal val owner: TurnController) :
        AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Token>
    }

    private val generation = AtomicLong()
    val current: Long get() = generation.get()
    fun begin(): Token = Token(generation.incrementAndGet(), this)
    fun cancel() { generation.incrementAndGet() }
    fun isCurrent(token: Token): Boolean = token.owner === this && token.generation == current

    suspend fun checkpoint() {
        val context = currentCoroutineContext()
        context.ensureActive()
        val token = context[Token] ?: return // Non-command lifecycle work has no turn capability.
        if (!isCurrent(token)) throw CancellationException("Superseded LEO turn")
    }
}
