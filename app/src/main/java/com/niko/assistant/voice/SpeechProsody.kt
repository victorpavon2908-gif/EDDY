package com.niko.assistant.voice

import com.niko.assistant.memory.MemoryLearning

data class SpeechProsody(val speed: Float = 1.07f, val pitch: Float = 0.92f) {
    companion object {
        fun forMode(base: SpeechProsody, mode: com.niko.assistant.modes.LeoMode, input: String = ""): SpeechProsody {
            val text = MemoryLearning.key(input)
            val explicit = Regex("\\b(?:mas despacio|habla lento|habla mas lento|habla despacio|habla mas despacio|habla rapido|habla mas rapido|responde rapido|ponte serio|habla serio|voz seria|sin bromas|habla alegre|habla asustado|habla enojado|voz de sorpresa)\\b")
            if (explicit.containsMatchIn(text)) return forInput(input)
            return when (mode) {
            com.niko.assistant.modes.LeoMode.NORMAL -> base
            com.niko.assistant.modes.LeoMode.SUPER -> SpeechProsody(speed = 1.15f, pitch = 0.89f)
            com.niko.assistant.modes.LeoMode.RESEARCH -> SpeechProsody(speed = 1.0f, pitch = 0.92f)
            com.niko.assistant.modes.LeoMode.CREATIVE -> SpeechProsody(speed = 1.10f, pitch = 1.02f)
            }
        }

        fun forInput(input: String): SpeechProsody {
            val text = MemoryLearning.key(input).replace(
                Regex("\\bno (?:estoy (?:triste|preocupado|preocupada)|me siento mal)\\b"), " ",
            )
            return when {
                Regex("\\b(?:mas despacio|habla (?:mas )?(?:lento|despacio)|estoy triste|me siento mal|estoy preocupado|estoy preocupada)\\b").containsMatchIn(text) -> SpeechProsody(speed = 0.93f)
                Regex("\\b(?:mas rapido|responde rapido|habla (?:mas )?rapido|date prisa|apurate|no dilates|voz tarda)\\b").containsMatchIn(text) -> SpeechProsody(speed = 1.15f)
                Regex("\\b(?:ponte serio|habla serio|modo serio|voz seria|sin bromas)\\b").containsMatchIn(text) -> SpeechProsody(speed = 0.96f, pitch = 0.89f)
                Regex("\\b(?:ponte sarcastico|habla sarcastico|modo sarcasmo|burlate|haz una broma)\\b").containsMatchIn(text) -> SpeechProsody(speed = 1.10f, pitch = 0.96f)
                Regex("\\b(?:alegrate|ponte feliz|habla alegre|emocionate|modo alegre|entusiasmado)\\b").containsMatchIn(text) -> SpeechProsody(speed = 1.14f, pitch = 1.02f)
                Regex("\\b(?:asustate|ten miedo|voz de miedo|habla asustado)\\b").containsMatchIn(text) -> SpeechProsody(speed = 1.12f, pitch = 1.04f)
                Regex("\\b(?:enojate|ponte enojado|habla enojado|modo enfadado)\\b").containsMatchIn(text) -> SpeechProsody(speed = 1.02f, pitch = 0.88f)
                Regex("\\b(?:voz de sorpresa|sorprendete|ponte sorprendido)\\b").containsMatchIn(text) -> SpeechProsody(speed = 1.12f, pitch = 1.05f)
                else -> if (com.niko.assistant.cognitive.LeoEmotionClassifier.needsGentleResponse(input)) SpeechProsody(speed = 0.96f) else SpeechProsody()
            }
        }

        fun fromExpressive(expressive: com.niko.assistant.cognitive.LeoEddyPersona.ExpressiveProsody): SpeechProsody {
            return SpeechProsody(speed = expressive.rateMultiplier, pitch = expressive.pitchMultiplier)
        }

        /** Prefer sentence/word boundaries over arbitrary character cuts for long replies. */
        fun chunks(text: String, limit: Int): List<String> {
            require(limit > 0)
            val chunks = mutableListOf<String>()
            var rest = text.trim()
            while (rest.length > limit) {
                val window = rest.take(limit)
                val sentence = window.indexOfLast { it in ".!?;\n" } + 1
                val space = window.lastIndexOf(' ')
                val cut = when { sentence >= limit / 2 -> sentence; space > 0 -> space; else -> limit }
                chunks.add(rest.take(cut).trim())
                rest = rest.drop(cut).trimStart()
            }
            if (rest.isNotBlank()) chunks.add(rest)
            return chunks
        }

        /**
         * Small first synthesis block so LEO starts talking sooner, then normal-sized blocks.
         * Avoids creating a tiny tail for medium replies because that would sound choppy.
         */
        fun fastStartChunks(text: String, firstLimit: Int = 48, nextLimit: Int = 96): List<String> {
            require(firstLimit > 0 && nextLimit > 0)
            val clean = text.trim()
            if (clean.isBlank()) return emptyList()
            if (clean.length <= firstLimit + 18) return listOf(clean)

            val window = clean.take(firstLimit)
            val sentence = window.indexOfLast { it in ".!?;\n" } + 1
            val space = window.lastIndexOf(' ')
            val cut = when {
                sentence >= firstLimit / 2 -> sentence
                space >= firstLimit / 2 -> space
                else -> firstLimit
            }
            val first = clean.take(cut).trim()
            val rest = clean.drop(cut).trimStart()
            if (first.isBlank() || rest.isBlank()) return listOf(clean)
            return listOf(first) + chunks(rest, nextLimit)
        }
    }
}
