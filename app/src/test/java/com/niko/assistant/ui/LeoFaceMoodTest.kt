package com.niko.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LeoFaceMoodTest {
    @Test fun supportsExplicitFacesWithAccents() {
        assertEquals(LeoFaceMood.LAUGHING, LeoFaceMood.resolve("LEO, reíte", ""))
        assertEquals(LeoFaceMood.WRY, LeoFaceMood.resolve("Sé sarcástico", ""))
        assertEquals(LeoFaceMood.SURPRISED, LeoFaceMood.resolve("Sorprendete", ""))
    }
    @Test fun onlyStripsAssistantNameAtWordBoundary() {
        assertEquals(LeoFaceMood.FRIENDLY, LeoFaceMood.resolve("Leoriete", ""))
        assertEquals(LeoFaceMood.LAUGHING, LeoFaceMood.resolve("LEO: reíte", ""))
    }
    @Test fun neverMocksDistress() {
        assertEquals(LeoFaceMood.CARING, LeoFaceMood.resolve("Estoy triste, contame un chiste", "Jajaja"))
    }
    @Test fun respectsNoJokes() {
        assertEquals(LeoFaceMood.FRIENDLY, LeoFaceMood.resolve("No te burles", "Sí, claro…"))
    }
    @Test fun quotedTopicsAndPunctuationAreNotEmotions() {
        assertEquals(LeoFaceMood.FRIENDLY, LeoFaceMood.resolve("Buscá qué significa reírse", "El texto dice jajaja. ¡Es una cita!"))
        assertEquals(LeoFaceMood.FRIENDLY, LeoFaceMood.resolve("", "¡Encontré tres fuentes!"))
    }
    @Test fun reactsToActualReplyCues() {
        assertEquals(LeoFaceMood.WRY, LeoFaceMood.resolve("", "Qué podría salir mal…"))
        assertEquals(LeoFaceMood.SURPRISED, LeoFaceMood.resolve("", "¡No me lo esperaba!"))
        assertEquals(LeoFaceMood.LAUGHING, LeoFaceMood.resolve("", "Jajaja, buena esa."))
    }
}
