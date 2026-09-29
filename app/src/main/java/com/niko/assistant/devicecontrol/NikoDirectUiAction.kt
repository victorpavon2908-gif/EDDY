package com.niko.assistant.devicecontrol

import java.text.Normalizer
import java.util.Locale

/** Fast, model-free actions for common commands over the currently visible screen. */
sealed interface NikoDirectUiAction {
    data class ClickLabel(val label: String) : NikoDirectUiAction
    data class TypeFocused(val text: String) : NikoDirectUiAction
    data object ScrollForward : NikoDirectUiAction
    data object ScrollBackward : NikoDirectUiAction
    data object Back : NikoDirectUiAction

    companion object {
        fun parse(request: String): NikoDirectUiAction? {
            val original = request.trim()
                .replace(Regex("(?i)^(?:leo|niko|nico)\\b[\\s,.:;!¿?¡-]*"), "")
                .trim()
            val normalized = normalize(original)
            if (normalized.isBlank()) return null

            if (Regex("\\b(?:desliza hacia abajo|baja un poco|segui bajando|sigue bajando)\\b").containsMatchIn(normalized)) {
                return ScrollForward
            }
            if (Regex("\\b(?:desliza hacia arriba|subi un poco|segui subiendo|sigue subiendo)\\b").containsMatchIn(normalized)) {
                return ScrollBackward
            }
            if (Regex("\\b(?:cerra|cierra|sali de) (?:esta |la )?(?:ventana|pantalla)\\b").containsMatchIn(normalized)) {
                return Back
            }

            TYPE.find(original)?.groupValues?.getOrNull(1)?.let { captured ->
                val suffix = FIELD_SUFFIX.find(captured)
                val value = (if (suffix == null) captured else captured.substring(0, suffix.range.first))
                    .trim(' ', '"', '\'', '.', ',')
                if (value.isNotBlank()) return TypeFocused(value.take(1_500))
            }

            CLICK.find(original)?.groupValues?.getOrNull(1)
                ?.replace(Regex("(?i)\\s+(?:por favor|porfa)$"), "")
                ?.trim(' ', '"', '\'', '.', ',')
                ?.takeIf(String::isNotBlank)
                ?.let { return ClickLabel(it.take(120)) }

            return null
        }

        private fun normalize(value: String): String = Normalizer
            .normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^a-z0-9ñ ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        private val TYPE = Regex(
            "(?i)^(?:por favor\\s+)?(?:escrib[ií]|escribe|tipe[aá]|tecle[aá])\\s+(.+)$",
        )
        private val CLICK = Regex(
            "(?i)^(?:por favor\\s+)?(?:toc[aá]|toca|presion[aá]|presiona|puls[aá]|pulsa|" +
                "seleccion[aá]|selecciona|eleg[ií]|elige|dale)\\s+(?:en\\s+|a\\s+|al\\s+)?(?:el\\s+|la\\s+)?" +
                "(?:(?:bot[oó]n|opci[oó]n)\\s+)?(?:que\\s+dice\\s+)?(.+)$",
        )
        private val FIELD_SUFFIX = Regex("(?i)\\s+en (?:el |la )?(?:campo|buscador|caja)(?: .*)?$")
    }
}
