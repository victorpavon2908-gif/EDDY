package com.niko.assistant.inference.local

import java.io.File
import java.security.MessageDigest

object LeoModelIntegrity {
    fun verify(file: File, bytes: Long, sha256: String, checkpoint: () -> Unit = {}) {
        require(file.isFile && file.length() == bytes) { "Tamaño de modelo incorrecto" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                checkpoint()
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == sha256) { "El modelo no coincide con el catálogo autorizado" }
    }
    fun resumedOffset(code: Int, range: String?, current: Long, expected: Long): Long {
        if (code == 200) return 0L
        require(code == 206 && current in 1 until expected) { "Respuesta de descarga inválida" }
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(range.orEmpty()) ?: error("Rango ausente")
        val (start, end, total) = match.destructured
        require(start.toLong() == current && total.toLong() == expected && end.toLong() == expected - 1) { "Rango de descarga incompatible" }
        return current
    }
}
