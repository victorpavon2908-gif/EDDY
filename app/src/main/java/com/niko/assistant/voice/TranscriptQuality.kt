package com.niko.assistant.voice

import java.text.Normalizer
import java.util.Locale

/** Lightweight quality gate used to decide when the optional Whisper pass is worth its latency. */
object TranscriptQuality {
    fun shouldRefine(text: String, sampleCount: Int, sampleRate: Int = 16_000): Boolean {
        val clean = normalize(text)
        if (clean.isBlank()) return true
        if (hasArtifacts(clean)) return true
        val seconds = sampleCount.toDouble() / sampleRate.coerceAtLeast(1)
        val words = clean.split(' ').filter(String::isNotBlank)
        // Repetitions can be intentional dictation. Do not remove or penalize them.
        if (words.size >= 12 && seconds > 0 && words.size / seconds > 7.5) return true
        return (seconds >= 3.0 && words.size == 1) ||
            (seconds >= 6.0 && words.size <= 2) ||
            (seconds >= 2.2 && words.size == 1 && words.first().length <= 3)
    }

    fun choose(primary: String, refinement: String, sampleCount: Int = 0): String {
        val a = normalize(primary)
        val b = normalize(refinement)
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        val aScore = score(a)
        val bScore = score(b)
        if (shouldRefine(a, sampleCount) && !shouldRefine(b, sampleCount) && bScore >= aScore) {
            // Recover missing words only when the longer decode retains the original ones.
            val originalTokens = tokens(a)
            var matched = 0
            for (token in tokens(b)) {
                if (matched < originalTokens.size && token == originalTokens[matched]) matched++
            }
            if (matched == originalTokens.size) return b
        }
        return if (bScore > aScore + 0.08) b else a
    }

    fun isUsable(text: String): Boolean = text.isNotBlank() && !hasArtifacts(text)

    /**
     * Corrige errores fonéticos comunes del reconocimiento de voz en español/nicaragüense
     * (nombres de aplicaciones, conectividades y wake-word) sin alterar números ni negaciones.
     */
    fun repairAsrTranscript(raw: String): String {
        if (raw.isBlank()) return ""
        var text = normalize(raw)
        val replacements = listOf(
            Regex("(?i)^(?:oye|hola|hey)\\s+(?:lio|leito|leos)\\b") to "Oye Leo",
            Regex("(?i)\\b(?:wasap|guasap|guasa|watsap|whatsap)\\b") to "WhatsApp",
            Regex("(?i)\\b(?:yutu|yutub|youtub|yu tube)\\b") to "YouTube",
            Regex("(?i)\\b(?:espotifai|spotifai|espotify)\\b") to "Spotify",
            Regex("(?i)\\b(?:blutu|blutut|bluethooth)\\b") to "Bluetooth",
            Regex("(?i)\\b(?:waifai|guaifai|wifi)\\b") to "Wi-Fi",
        )
        for ((pattern, replacement) in replacements) {
            text = pattern.replace(text, replacement)
        }
        return text.trim()
    }

    private val WAKE_PATTERN = Regex("(?i)(?:^|\\s|[¿¡,.;:!?])leo(?:\\s|[¿¡,.;:!?]|$)")

    fun extractRepairedWakeCommand(value: String): String? {
        val repaired = repairAsrTranscript(value.trim())
        val match = WAKE_PATTERN.find(repaired) ?: return null
        return repaired.substring(match.range.last + 1)
            .trim()
            .trimStart(',', '.', ';', ':', '¿', '?', '¡', '!')
            .trim()
    }

    /** A costly independent decode is reserved for low-quality, execution-sensitive dictation. */
    fun requiresAcousticVerification(value: String): Boolean {
        val normalized = normalizedForRules(value)
        return tokens(normalized).any(::isCritical) || HIGH_RISK_DICTATION_CUE.containsMatchIn(normalized)
    }

    fun requiresClarification(primary: String, refinement: String, sampleCount: Int): Boolean {
        if (!isUsable(primary) || !isUsable(refinement)) return false
        val a = tokens(primary)
        val b = tokens(refinement)
        // A disagreeing number or negation must never silently become an executed command.
        if (a.filter(::isCritical) != b.filter(::isCritical)) return true
        // Names, addresses, times and message bodies are literal data. If two independent
        // decoders disagree on their tokens, LEO asks again instead of inventing a compromise.
        if (literalSensitive(primary) || literalSensitive(refinement)) {
            if (a != b) return true
        }
        if (shouldRefine(primary, sampleCount)) return false
        val common = a.toSet().intersect(b.toSet()).size
        return common.toDouble() / maxOf(a.toSet().size, b.toSet().size, 1) < 0.4
    }

    fun score(text: String): Double {
        val clean = normalize(text)
        if (clean.isBlank()) return 0.0
        var score = 1.0
        if (hasArtifacts(clean)) score -= 0.65
        val visible = clean.count { !it.isWhitespace() }.coerceAtLeast(1)
        val lettersAndDigits = clean.count { it.isLetterOrDigit() }
        val ratio = lettersAndDigits.toDouble() / visible
        if (ratio < 0.70) score -= (0.70 - ratio).coerceAtMost(0.35)
        return score.coerceIn(0.0, 1.0)
    }

    private fun hasArtifacts(value: String): Boolean {
        val lowered = value.lowercase(Locale.ROOT)
        return listOf("<unk>", "[unk]", "<|", "|>", "�", "字幕", "music playing").any(lowered::contains)
    }

    private fun literalSensitive(value: String): Boolean {
        val normalized = normalizedForRules(value)
        return LITERAL_CUES.any(normalized::contains) ||
            CONTACT_OR_MESSAGE_CUE.containsMatchIn(normalized) ||
            Regex("\\b\\d{1,}\\b").containsMatchIn(normalized)
    }

    private fun tokens(value: String): List<String> = Regex("[\\p{L}\\p{N}]+")
        .findAll(Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), ""))
        .map { it.value }.toList()

    private fun isCritical(token: String): Boolean = token.any(Char::isDigit) || token in CRITICAL_WORDS

    private val CRITICAL_WORDS = setOf(
        "no", "nunca", "jamas", "tampoco", "sin", "cero", "uno", "dos", "tres", "cuatro",
        "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce", "trece", "catorce",
        "quince", "dieciseis", "diecisiete", "dieciocho", "diecinueve", "veinte", "treinta",
        "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa", "cien", "ciento", "mil",
    )

    private val CONTACT_OR_MESSAGE_CUE = Regex(
        "\\b(?:llama(?:me|le)?|llamar|marca|escribi(?:le)?|escribe(?:le)?|manda(?:le)?|envia(?:le)?|" +
            "decile|dile|mensaje|whatsapp|contacto)\\b",
    )

    private val HIGH_RISK_DICTATION_CUE = Regex(
        "\\b(?:llama(?:me|le)?|llamar|marca|escribi(?:le)?|escribe(?:le)?|manda(?:le)?|envia(?:le)?|" +
            "decile|dile|mensaje|contacto)\\b",
    )

    private val LITERAL_CUES = listOf(
        "numero", "direccion", "calle", "avenida", "a las ", "alarma", "cordoba", "dolar",
        "mensaje que", "decile que", "dile que", "casa ", "kilometro", "km ",
    )

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFC)
        return decomposed.replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .trim()
    }

    private fun normalizedForRules(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
