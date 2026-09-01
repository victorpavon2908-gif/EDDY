package com.eddy.assistant.voice

/** Bounded audio history; the audio worker is its sole owner. */
internal class PcmPreRoll(private val capacity: Int) {
    private val data = FloatArray(capacity.also { require(it > 0) })
    private var end = 0
    private var size = 0
    fun append(samples: FloatArray) {
        for (sample in samples) { data[end] = sample; end = (end + 1) % capacity; if (size < capacity) size++ }
    }
    fun snapshot(): FloatArray = FloatArray(size) { data[(end - size + it + capacity) % capacity] }
    fun clear() { end = 0; size = 0 }
}
