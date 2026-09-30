package com.niko.assistant.brain

/** Receives normalized speech. Only explicit music requests belong to this tool. */
object MusicCommand {
    fun query(text: String): String? = Regex(
        "^(?:busca|buscame|buscar) (?:musica|canciones)(?: de| sobre)? (.+)$"
    ).matchEntire(text)?.groupValues?.get(1)?.trim()?.take(200)?.takeIf { it.isNotBlank() }
}
