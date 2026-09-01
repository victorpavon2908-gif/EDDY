package com.eddy.assistant.voice

import java.util.Locale

object OfflineVoiceSelector {
    data class Candidate(
        val name: String,
        val language: String,
        val country: String,
        val quality: Int,
        val latency: Int,
        val networkRequired: Boolean,
        val features: Set<String> = emptySet(),
    )

    fun ranked(voices: List<Candidate>, savedName: String?): List<Candidate> = voices
        .filter { it.language.equals("es", true) && !it.networkRequired && "notInstalled" !in it.features }
        .sortedWith(compareBy<Candidate> { if (it.name == savedName) 0 else 1 }
            .thenBy { countryRank(it.country) }
            .thenBy { naturalRank(it) }
            .thenBy { genderRank(it) }
            .thenByDescending { it.quality }
            .thenBy { it.latency }
            .thenBy { it.name })

    private fun words(voice: Candidate): Set<String> = (voice.name + " " + voice.features.joinToString(" "))
        .lowercase(Locale.ROOT).split(Regex("[^\\p{L}]+")).toSet()

    private fun naturalRank(voice: Candidate): Int =
        if (words(voice).any { it in setOf("natural", "neural", "wavenet", "premium", "enhanced", "studio") }) 0 else 1

    private fun genderRank(voice: Candidate): Int = when {
        words(voice).any { it in setOf("female", "femenino", "feminine", "mujer", "woman") } -> 2
        words(voice).any { it in setOf("male", "masculino", "masculine", "hombre", "man") } -> 0
        else -> 1
    }

    private fun countryRank(country: String): Int = when (country.uppercase(Locale.ROOT)) {
        "NI" -> 0
        "CR", "HN", "SV", "GT" -> 1
        "US", "MX" -> 2
        "CO", "VE", "PA", "DO", "PR" -> 3
        "AR", "UY", "CL", "PE", "EC", "BO", "PY" -> 4
        "ES" -> 8
        else -> 6
    }
}
