package com.niko.assistant.ui.generated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeneratedFormulaEngineTest {
    @Test fun evaluatesLinkedNumericValues() {
        val values = mapOf("buenas" to 900.0, "meta" to 1000.0, "defectos" to 10.0)
        assertEquals(
            90.0,
            GeneratedFormulaEngine.evaluate("(buenas/meta)*100", values)!!,
            0.0001,
        )
        assertEquals(
            890.0,
            GeneratedFormulaEngine.evaluate("buenas-defectos", values)!!,
            0.0001,
        )
    }

    @Test fun rejectsInvalidOrIncompleteExpressions() {
        assertNull(GeneratedFormulaEngine.evaluate("a+b", mapOf("a" to 1.0)))
        assertNull(GeneratedFormulaEngine.evaluate("10/0", emptyMap()))
        assertNull(GeneratedFormulaEngine.evaluate("texto()", emptyMap()))
    }
}
