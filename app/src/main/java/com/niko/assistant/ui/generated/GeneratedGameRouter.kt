package com.niko.assistant.ui.generated

internal enum class GeneratedGameKind {
    FALLING_BLOCKS,
    ARCADE_LANES,
}

internal fun resolveGeneratedGameKind(raw: String): GeneratedGameKind {
    val text = raw.lowercase()
    return when {
        listOf(
            "tetris", "tetrix", "tettrix", "tétris", "bloques", "bloques que caen",
            "falling blocks", "encajar bloques",
        ).any(text::contains) -> GeneratedGameKind.FALLING_BLOCKS
        else -> GeneratedGameKind.ARCADE_LANES
    }
}
