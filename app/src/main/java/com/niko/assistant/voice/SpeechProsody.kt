package com.niko.assistant.voice

import com.niko.assistant.memory.MemoryLearning

data class SpeechProsody(val speed: Float = 1.07f, val pitch: Float = 0.92f) {
    companion object {
        fun forInput(input: String): SpeechProsody {
            val text = MemoryLearning.key(input).replace(
                Regex("\\bno (?:estoy (?:triste|preocupado|preocupada)|me siento mal)\\b"), " ",
            )
            return when {
                Regex("\\b(?:mas despacio|habla lento|estoy triste|me siento mal|estoy preocupado|estoy preocupada)\\b").containsMatchIn(text) -> SpeechProsody(speed = 0.93f)
                Regex("\\b(?:mas rapido|responde rapido|habla rapido|date prisa|apurate|no dilates|voz tarda)\\b").containsMatchIn(text) -> SpeechProsody(speed = 1.15f)
                else -> SpeechProsody()
            }
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
