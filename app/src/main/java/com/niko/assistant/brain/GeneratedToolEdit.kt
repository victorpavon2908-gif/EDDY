package com.niko.assistant.brain

import java.text.Normalizer
import java.util.Locale

/** Explicit, additive edits only. Questions, negations and web content cannot trigger edits. */
object GeneratedToolEdit {
    fun componentName(request: String): String? {
        val normalized = Normalizer.normalize(request.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").trim().trimEnd('.', '!')
        return Regex("^(?:agrega|agregale|anadi|anade|anadele|suma) (?:un |una )?([a-z ]{3,40}) a (?:la|mi|esta) herramienta$")
            .matchEntire(normalized)?.groupValues?.get(1)
    }
}
