package com.eddy.assistant.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EddyMathEngineTest {
    @Test
    fun solvesBasicAddition() {
        assertEquals("8", EddyMathEngine.solve("Cuánto es 4 + 4"))
    }

    @Test
    fun respectsOperatorPrecedence() {
        assertEquals("14", EddyMathEngine.solve("2 + 3 por 4"))
    }

    @Test
    fun solvesParentheses() {
        assertEquals("20", EddyMathEngine.solve("calculame (2 + 3) * 4"))
    }

    @Test
    fun solvesPercentage() {
        assertEquals("100", EddyMathEngine.solve("20 por ciento de 500"))
    }

    @Test
    fun solvesPowerAndSquareRoot() {
        assertEquals("256", EddyMathEngine.solve("2 elevado a 8"))
        assertEquals("9", EddyMathEngine.solve("raíz cuadrada de 81"))
    }

    @Test
    fun refusesDivisionByZeroAndNormalSpeech() {
        assertNull(EddyMathEngine.solve("10 / 0"))
        assertNull(EddyMathEngine.solve("abrí YouTube"))
    }
}
