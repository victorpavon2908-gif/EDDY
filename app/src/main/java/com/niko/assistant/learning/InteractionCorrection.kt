package com.niko.assistant.learning

/** Extracts an explicit correction so the corrected request can run and supervise learning. */
object InteractionCorrection {
    fun correctedText(input: String): String? {
        val clean = input.trim().replace(Regex("(?i)^(?:leo|lio|niko|nico)[, :;-]*"), "")
        val match = CORRECTION.find(clean) ?: return null
        return match.groupValues[1]
            .trim(' ', ',', '.', ':', ';', '¿', '?', '¡', '!')
            .take(1_500)
            .takeIf { it.length >= 2 }
    }

    private val CORRECTION = Regex(
        "(?is)^(?:no\\s*,\\s*)?(?:quise decir|queria decir|quería decir|te dije|lo que dije fue|" +
            "lo que quise decir (?:fue|era)|me referia a|me refería a)\\s+(?:que\\s+)?(.+)$",
    )
}
