package com.niko.assistant.memory.embedding

import java.io.File
import java.security.MessageDigest

/** Immutable upstream artifacts. Never execute code or deserialize pickle from a model repo. */
object LeoEmbeddingModel {
    const val REPOSITORY = "sentence-transformers/distiluse-base-multilingual-cased-v2"
    const val REVISION = "bfe45d0732ca50787611c0fe107ba278c7f3f889"
    const val DIMENSIONS = 512
    data class Artifact(val path: String, val name: String, val bytes: Long, val sha256: String) {
        val url: String get() = "https://huggingface.co/$REPOSITORY/resolve/$REVISION/$path"
        fun valid(directory: File): Boolean {
            val file = File(directory, name)
            return file.isFile && file.length() == bytes && digest(file) == sha256
        }
    }
    // U8U8 avoids U8S8 saturation on x86 CPUs without VNNI; also supported on ARM.
    // The upstream filename describes the export recipe, not a native ISA requirement.
    val artifacts = listOf(
        Artifact("onnx/model_quint8_avx2.onnx", "model.onnx", 135377779, "6a5852e0da9ca0e4532274b6c5eed71f9938fa8ff15e8345c6873a1969093f80"),
        Artifact("vocab.txt", "vocab.txt", 995526, "fe0fda7c425b48c516fc8f160d594c8022a0808447475c1a7c6d6479763f310c"),
        Artifact("2_Dense/model.safetensors", "dense.safetensors", 1575104, "0a21b1ce908e772ebf09f93c20ca09524c32706e9918d9c0169a3f0663b191ed"),
    )
    fun verify(directory: File): Boolean = artifacts.all { it.valid(directory) }
    private fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
