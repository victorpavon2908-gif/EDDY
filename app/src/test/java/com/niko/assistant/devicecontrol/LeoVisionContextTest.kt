package com.niko.assistant.devicecontrol

import org.junit.Assert.*
import org.junit.Test

class LeoVisionContextTest {
    @Test fun emptyInputNeverClaimsVision() {
        val context = LeoVisionContext(100, "", 1, 1, "", 0)
        assertFalse(context.available); assertFalse(context.usableAt(101))
        assertTrue(context.offlineDescription().contains("No recibí"))
    }
    @Test fun descriptionUsesOnlyObservedLabels() {
        val context = LeoVisionContext(100, "app", 1, 2, "[node_1] Button [text=\"Error de conexión\"]", 1)
        assertTrue(context.offlineDescription().contains("Error de conexión"))
        assertTrue(context.offlineDescription().contains("No estoy viendo imágenes"))
        assertFalse(context.usableAt(10_101))
    }
    @Test fun conversationalPronounIsNotAutomaticallyScreenCapture() {
        assertFalse(LeoVisionContext.isExplicitScreenRequest("Eso último explicámelo más sencillo"))
        assertFalse(LeoVisionContext.isExplicitScreenRequest("explicame esto"))
        assertTrue(LeoVisionContext.isExplicitScreenRequest("¿Qué error aparece en pantalla?"))
    }
}
