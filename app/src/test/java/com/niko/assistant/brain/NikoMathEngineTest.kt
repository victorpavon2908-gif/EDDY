package com.niko.assistant.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NikoMathEngineTest {
    @Test
    fun solvesBasicAddition() {
        assertEquals("8", NikoMathEngine.solve("Cuánto es 4 + 4"))
    }

    @Test
    fun respectsOperatorPrecedence() {
        assertEquals("14", NikoMathEngine.solve("2 + 3 por 4"))
    }

    @Test
    fun solvesParentheses() {
        assertEquals("20", NikoMathEngine.solve("calculame (2 + 3) * 4"))
    }

    @Test
    fun solvesPercentage() {
        assertEquals("100", NikoMathEngine.solve("20 por ciento de 500"))
    }

    @Test
    fun solvesPowerAndSquareRoot() {
        assertEquals("256", NikoMathEngine.solve("2 elevado a 8"))
        assertEquals("9", NikoMathEngine.solve("raíz cuadrada de 81"))
    }

    @Test
    fun refusesDivisionByZeroAndNormalSpeech() {
        assertNull(NikoMathEngine.solve("10 / 0"))
        assertNull(NikoMathEngine.solve("abrí YouTube"))
    }
}
