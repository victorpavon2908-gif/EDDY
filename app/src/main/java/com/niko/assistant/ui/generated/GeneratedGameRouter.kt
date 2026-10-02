package com.niko.assistant.ui.generated

internal enum class GeneratedGameKind {
    FALLING_BLOCKS,
    SNAKE,
    PADDLE,
    MEMORY,
    BILLIARDS,
    PLATFORMER,
    TARGET_TAP,
    ARCADE_LANES,
}

internal fun resolveGeneratedGameKind(raw: String): GeneratedGameKind {
    val text = raw.lowercase()
    return when {
        listOf(
            "tetris", "tetrix", "tettrix", "tétris", "bloques", "bloques que caen",
            "falling blocks", "encajar bloques",
        ).any(text::contains) -> GeneratedGameKind.FALLING_BLOCKS

        listOf(
            "snake", "serpiente", "culebra", "gusano",
        ).any(text::contains) -> GeneratedGameKind.SNAKE

        listOf(
            "pong", "ping pong", "ping-pong", "paletas", "paddle",
        ).any(text::contains) -> GeneratedGameKind.PADDLE

        listOf(
            "memoria", "memory", "parejas", "cartas iguales", "matching cards",
        ).any(text::contains) -> GeneratedGameKind.MEMORY

        listOf(
            "billar", "billares", "pool", "billiard", "billiards", "bola 8", "8 ball",
        ).any(text::contains) -> GeneratedGameKind.BILLIARDS

        listOf(
            "plataforma", "plataformas", "platformer", "saltar", "saltos", "tipo mario",
        ).any(text::contains) -> GeneratedGameKind.PLATFORMER

        listOf(
            "tiro", "tiros", "disparo", "disparos", "shooter", "blancos", "target",
            "golpea", "golpear", "toca objetivos",
        ).any(text::contains) -> GeneratedGameKind.TARGET_TAP

        else -> GeneratedGameKind.ARCADE_LANES
    }
}
