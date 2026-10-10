package com.niko.assistant.memory.embedding

/** Minimal JNI binding to Sherpa's existing ONNX C runtime, API version 22. */
internal object LeoOrtBridge {
    init { System.loadLibrary("leo_semantic_jni") }
    external fun open(path: String): Long
    external fun meanEmbedding(handle: Long, ids: LongArray): FloatArray
    external fun close(handle: Long)
}
