package com.niko.assistant.inference.local

/** Loaded only in the separate inference process, never in the microphone process. */
object LeoLlamaBridge {
    init { System.loadLibrary("leo_llama_jni") }
    external fun generate(path: String, prompt: ByteArray, request: Long, maxTokens: Int): ByteArray?
    external fun cancel(request: Long)
}
