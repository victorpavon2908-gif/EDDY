package com.niko.assistant.devicecontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoUiTaskPolicyTest {
    @Test fun acceptsConcreteScreenActionsInNaturalSpanish() {
        assertTrue(NikoUiTaskPolicy.looksLikeExplicitUiTask("Leo, tocá el botón Continuar"))
        assertTrue(NikoUiTaskPolicy.looksLikeExplicitUiTask("Leo, dale al botón Continuar"))
        assertTrue(NikoUiTaskPolicy.looksLikeExplicitUiTask("escribí Masaya en el buscador"))
        assertTrue(NikoUiTaskPolicy.looksLikeExplicitUiTask("bajá un poco en esta pantalla"))
    }

    @Test fun rejectsQuestionsNegationsAndSensitiveActions() {
        assertFalse(NikoUiTaskPolicy.looksLikeExplicitUiTask("cómo puedo tocar ese botón"))
        assertFalse(NikoUiTaskPolicy.looksLikeExplicitUiTask("Leo, decime cómo puedo tocar ese botón"))
        assertFalse(NikoUiTaskPolicy.looksLikeExplicitUiTask("no presionés Continuar"))
        assertFalse(NikoUiTaskPolicy.looksLikeExplicitUiTask("tocá confirmar compra"))
        assertFalse(NikoUiTaskPolicy.looksLikeExplicitUiTask("escribí mi contraseña secreta"))
        assertFalse(NikoUiTaskPolicy.looksLikeExplicitUiTask("mandá el mensaje"))
    }
}
