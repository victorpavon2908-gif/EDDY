package com.niko.assistant.learning

import com.niko.assistant.memory.MemoryLearning

/** Keeps trainable examples useful while refusing credentials and long numeric secrets. */
object AdaptiveLearningPolicy {
    fun example(input: String): String? {
        val raw = MemoryLearning.key(input)
        if (raw.isBlank() || !canPersistLiteral(input)) return null
        return raw
            .replace(Regex("\\b\\d{4,}\\b"), " numero ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_EXAMPLE_CHARS)
            .takeIf { it.length >= 2 }
    }

    fun canPersistLiteral(input: String): Boolean {
        val raw = MemoryLearning.key(input)
        return raw.isNotBlank() && SECRET_MARKERS.none(raw::contains) &&
            !Regex("(?i)\\b[\\w.+-]+@[\\w.-]+\\.[a-z]{2,}\\b").containsMatchIn(input) &&
            !Regex("(?<!\\d)(?:\\d[\\s().+-]*){6,}(?!\\d)").containsMatchIn(input)
    }

    private const val MAX_EXAMPLE_CHARS = 384
    private val SECRET_MARKERS = setOf(
        "contrasena", "password", "codigo de verificacion", "api key", "api_key", "secret key",
        "seed phrase", "frase semilla", "clave privada", "private key", "token de acceso", "cvv", "pin de",
    )
}
