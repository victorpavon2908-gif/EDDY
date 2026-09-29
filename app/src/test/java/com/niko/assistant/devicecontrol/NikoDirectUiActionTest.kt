package com.niko.assistant.devicecontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NikoDirectUiActionTest {
    @Test fun extractsLabelsAndDictationWithoutAModel() {
        assertEquals(
            NikoDirectUiAction.ClickLabel("Continuar"),
            NikoDirectUiAction.parse("Leo, dale al botón que dice Continuar"),
        )
        assertEquals(
            NikoDirectUiAction.TypeFocused("Masaya"),
            NikoDirectUiAction.parse("escribí Masaya en el buscador"),
        )
    }

    @Test fun recognizesScrollingAndClosing() {
        assertEquals(NikoDirectUiAction.ScrollForward, NikoDirectUiAction.parse("bajá un poco"))
        assertEquals(NikoDirectUiAction.ScrollBackward, NikoDirectUiAction.parse("subí un poco"))
        assertEquals(NikoDirectUiAction.Back, NikoDirectUiAction.parse("cerrá esta pantalla"))
        assertNull(NikoDirectUiAction.parse("contame qué aparece aquí"))
    }
}
