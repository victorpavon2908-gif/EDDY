package com.niko.assistant.voice

import java.text.Normalizer
import java.util.Locale

/** Lightweight quality gate used to decide when the optional Whisper pass is worth its latency. */
object TranscriptQuality {
    fun shouldRefine(text: String, sampleCount: Int, sampleRate: Int = 16_000): Boolean {
        val clean = normalize(text)
        if (clean.isBlank()) return true
        if (hasArtifacts(clean) || repeatedTokenRatio(clean) >= 0.55) return true
        val seconds = sampleCount.toDouble() / sampleRate.coerceAtLeast(1)
        val words = clean.split(' ').filter(String::isNotBlank)
        // A long utterance collapsing to one tiny token is usually an ASR miss. Genuine
        // one-word commands remain on Canary so "apagála"/"cámara" stay instant.
        return seconds >= 2.2 && words.size == 1 && words.first().length <= 3
    }

    fun choose(primary: String, refinement: String): String {
        val a = normalize(primary)
        val b = normalize(refinement)
        if (a.isBlank()) return b
        if (b.isBlank()) return a
        val aScore = score(a)
        val bScore = score(b)
        return if (bScore > aScore + 0.08) b else a
    }

    fun score(text: String): Double {
        val clean = normalize(text)
        if (clean.isBlank()) return 0.0
        var score = 1.0
        if (hasArtifacts(clean)) score -= 0.65
        score -= repeatedTokenRatio(clean) * 0.45
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

    private fun repeatedTokenRatio(value: String): Double {
        val words = value.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.length > 1 }
        if (words.size < 3) return 0.0
        val maxCount = words.groupingBy { it }.eachCount().values.maxOrNull() ?: 1
        return maxCount.toDouble() / words.size
    }

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFC)
        return decomposed.replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.;:!?])"), "$1")
            .trim()
    }
}
