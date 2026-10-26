package com.niko.assistant.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals("170", NikoMathEngine.solve("200 menos 15%"))
        assertEquals("230", NikoMathEngine.solve("200 más el 15 por ciento"))
        assertEquals("170", NikoMathEngine.solve("descuento del 15% a 200"))
        assertEquals("20", NikoMathEngine.solve("qué porcentaje es 30 de 150"))
    }

    @Test
    fun solvesPowerAndSquareRoot() {
        assertEquals("256", NikoMathEngine.solve("2 elevado a 8"))
        assertEquals("9", NikoMathEngine.solve("raíz cuadrada de 81"))
        assertEquals("3", NikoMathEngine.solve("raíz cúbica de 27"))
    }

    @Test
    fun solvesScientificFunctionsAndWordNumbers() {
        assertEquals("0.5", NikoMathEngine.solve("seno de 30"))
        assertEquals("0.5", NikoMathEngine.solve("coseno de 60"))
        assertEquals("1", NikoMathEngine.solve("tangente de 45"))
        assertEquals("3", NikoMathEngine.solve("logaritmo de 1000"))
        assertEquals("1", NikoMathEngine.solve("logaritmo natural de e"))
        assertEquals("120", NikoMathEngine.solve("factorial de 5"))
        assertEquals("120", NikoMathEngine.solve("5!"))
        assertEquals("42", NikoMathEngine.solve("valor absoluto de -42"))
        assertEquals("60", NikoMathEngine.solve("cuánto es doce por cinco"))
    }

    @Test
    fun convertsNaturalLanguageUnits() {
        assertEquals("100 km equivalen a 62.1371 mi.", NikoMathEngine.convertUnits("convertí 100 kilómetros a millas"))
        assertEquals("100 °C equivalen a 212 °F.", NikoMathEngine.convertUnits("cuánto es 100 grados celsius en fahrenheit"))
        assertEquals("2 GB equivalen a 2048 MB.", NikoMathEngine.convertUnits("cuántos megabytes son 2 gigabytes"))
        assertEquals("10 kg equivalen a 22.0462 lb.", NikoMathEngine.convertUnits("10 kilos a libras"))
        assertNull(NikoMathEngine.convertUnits("10 kilos a kilómetros"))
    }

    @Test
    fun refusesDivisionByZeroAndNormalSpeech() {
        assertNull(NikoMathEngine.solve("10 / 0"))
        assertNull(NikoMathEngine.solve("abrí YouTube"))
        assertTrue(NikoMathEngine.solve("raíz cuadrada de -9") == null)
    }
}
