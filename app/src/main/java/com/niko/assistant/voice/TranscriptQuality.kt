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

    fun requiresClarification(primary: String, refinement: String, sampleCount: Int): Boolean {
        if (!isUsable(primary) || !isUsable(refinement)) return false
        val a = tokens(primary)
        val b = tokens(refinement)
        // A disagreeing number or negation must never silently become an executed command.
        if (a.filter(::isCritical) != b.filter(::isCritical)) return true
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

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFC)
        return decomposed.replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .trim()
    }
}
