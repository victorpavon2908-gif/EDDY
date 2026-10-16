package com.niko.assistant.ui

import java.text.Normalizer
import java.util.Locale

/** Conservative visual cues, not emotion recognition or a claim about the user's feelings. */
internal enum class LeoFaceMood {
    FRIENDLY, LAUGHING, WRY, SURPRISED, CARING, AFRAID, ANGRY, SAD;

    companion object {
        private fun key(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "").trim()

        fun resolve(input: String, reply: String): LeoFaceMood {
            val user = key(input).replace(Regex("^leo\\b[ ,:]*"), "")
            val answer = key(reply)
            com.niko.assistant.ai.LeoExpression.requested(input)?.let { requested ->
                return when (requested) {
                    com.niko.assistant.ai.LeoExpression.HAPPY, com.niko.assistant.ai.LeoExpression.NEUTRAL -> FRIENDLY
                    com.niko.assistant.ai.LeoExpression.LAUGH -> LAUGHING
                    com.niko.assistant.ai.LeoExpression.WRY -> WRY
                    com.niko.assistant.ai.LeoExpression.SURPRISE -> SURPRISED
                    com.niko.assistant.ai.LeoExpression.FEAR -> AFRAID
                    com.niko.assistant.ai.LeoExpression.ANGRY -> ANGRY
                    com.niko.assistant.ai.LeoExpression.SAD -> SAD
                }
            }
            if (com.niko.assistant.ai.LeoExpression.asksForSelfJoke(input)) return WRY
            val sensitive = Regex("\\b(triste|muerte|murio|duelo|enfermo|enferma|dolor|emergencia|peligro|deprimido|deprimida|suicidio)\\b")
            if (sensitive.containsMatchIn(user + " " + answer)) return CARING
            if (com.niko.assistant.cognitive.LeoEmotionClassifier.needsGentleResponse(input)) return CARING
            if (Regex("\\b(no te rias|no te burles|sin bromas|habla en serio|no hagas bromas)\\b").containsMatchIn(user)) return FRIENDLY
            return when {
                Regex("^(rei(?:te)?|riete|sonrei|sonrie|contame un chiste|cuentame un chiste)([ .!?]|$)").containsMatchIn(user) -> LAUGHING
                Regex("^(se sarcastico|hace una cara sarcastica|haz una cara sarcastica|burlate|hace una mueca)([ .!?]|$)").containsMatchIn(user) -> WRY
                Regex("^(sorprendete|sorprendite|pone cara de sorpresa|haz cara de sorpresa)([ .!?]|$)").containsMatchIn(user) -> SURPRISED
                Regex("^(jajaja|ja, ja|jaja|jeje|😂|🤣)").containsMatchIn(answer) -> LAUGHING
                Regex("^(¡?vaya sorpresa|¡?no me lo esperaba|¡?increible|😮|😲)").containsMatchIn(answer) -> SURPRISED
                Regex("^(si, claro[.…]|que podria salir mal|vaya, que conveniente|😏)").containsMatchIn(answer) -> WRY
                else -> FRIENDLY
            }
        }
    }
}
