package com.niko.assistant.ui

/** Monotonic time keeps countdowns accurate after UI suspension or delayed frames. */
object CountdownClock {
    fun remaining(deadline: Long, now: Long): Long = (deadline - now).coerceAtLeast(0L)
}
