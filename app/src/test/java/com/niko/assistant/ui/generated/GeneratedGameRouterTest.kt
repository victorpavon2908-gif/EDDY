package com.niko.assistant.ui.generated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedGameRouterTest {
    @Test fun tetrisSpellingVariantsUseFallingBlocksEngine() {
        listOf(
            "juego de tetris",
            "juego de tetrix",
            "juego de tettrix",
            "bloques que caen",
            "encajar bloques",
        ).forEach { text ->
            assertEquals(text, GeneratedGameKind.FALLING_BLOCKS, resolveGeneratedGameKind(text))
        }
        assertEquals(
            GeneratedGameKind.ARCADE_LANES,
            resolveGeneratedGameKind("juego de moto"),
        )
    }

    @Test fun routesCommonGameFamiliesToDedicatedEngines() {
        val cases = mapOf(
            "juego de snake" to GeneratedGameKind.SNAKE,
            "serpiente clásica" to GeneratedGameKind.SNAKE,
            "pong de dos paletas" to GeneratedGameKind.PADDLE,
            "juego de memoria con cartas" to GeneratedGameKind.MEMORY,
            "billar bola 8" to GeneratedGameKind.BILLIARDS,
            "juego de plataformas tipo mario" to GeneratedGameKind.PLATFORMER,
            "shooter de blancos" to GeneratedGameKind.TARGET_TAP,
            "juego de moto" to GeneratedGameKind.ARCADE_LANES,
        )
        cases.forEach { (request, expected) ->
            assertEquals(request, expected, resolveGeneratedGameKind(request))
        }
    }

    @Test fun completedRowsAreRemoved() {
        val board = List(18) { row ->
            if (row == 17) List(10) { true } else List(10) { false }
        }
        val (cleared, count) = clearCompletedRows(board)
        assertEquals(1, count)
        assertEquals(18, cleared.size)
        assertFalse(cleared.first().any { it })
    }

    @Test fun placementRejectsWallsAndOccupiedCells() {
        val empty = List(18) { List(10) { false } }
        assertTrue(canPlace(empty, listOf(0 to 0, 1 to 0)))
        assertFalse(canPlace(empty, listOf(-1 to 0)))
        val occupied = empty.mapIndexed { y, row ->
            row.mapIndexed { x, value -> value || (x == 4 && y == 4) }
        }
        assertFalse(canPlace(occupied, listOf(4 to 4)))
    }
}
