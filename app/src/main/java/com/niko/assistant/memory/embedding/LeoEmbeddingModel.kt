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
    val artifacts = listOf(
        Artifact("onnx/model_qint8_arm64.onnx", "model.onnx", 135336307, "c78c64bb0446ceb7086d2de24c2f4297e7a909eb457b4f68e5fdb6ab19f285c0"),
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
