package com.niko.assistant.devicecontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoVisualContextTest {
    @Test
    fun detectsExplicitScreenQuestions() {
        assertTrue(NikoVisualContext.wantsScreenContext("Niko, ¿qué ves?"))
        assertTrue(NikoVisualContext.wantsScreenContext("Nico mirá esto que aparece aquí"))
        assertTrue(NikoVisualContext.wantsScreenContext("¿Qué dice esta pantalla?"))
        assertTrue(NikoVisualContext.wantsScreenContext("Ayudame con esto que sale en la pantalla"))
    }

    @Test
    fun unrelatedQuestionsDoNotReadScreen() {
        assertFalse(NikoVisualContext.wantsScreenContext("qué ves de Bitcoin este año"))
        assertFalse(NikoVisualContext.wantsScreenContext("buscá qué dice la ley sobre vacaciones"))
        assertFalse(NikoVisualContext.wantsScreenContext("abrí YouTube"))
        assertFalse(NikoVisualContext.wantsScreenContext("prendé la linterna"))
    }
}
