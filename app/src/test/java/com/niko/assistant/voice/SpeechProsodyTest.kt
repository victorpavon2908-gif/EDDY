package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechProsodyTest {
    @Test fun speechSpeedRespectsExplicitRequestsAndDistress() {
        assertTrue(SpeechProsody.forInput("hablá más despacio").speed < 1f)
        assertTrue(SpeechProsody.forInput("estoy triste").speed < 1f)
        assertTrue(SpeechProsody.forInput("hablá más rápido").speed > 1f)
        assertEquals(SpeechProsody(), SpeechProsody.forInput("qué hora es"))
        assertEquals(SpeechProsody(), SpeechProsody.forInput("no estoy triste"))
    }

    @Test fun longSpeechKeepsWordsAndPunctuationAcrossChunks() {
        val original = "Una frase completa. Otra frase con más detalle. Terminamos aquí."
        val parts = SpeechProsody.chunks(original, 30)
        assertEquals(original, parts.joinToString(" "))
        assertTrue(parts.all { it.length <= 30 })
        assertEquals(listOf("Hola."), SpeechProsody.chunks("Hola.", 30))
    }

    @Test fun emotionalAndSpeedRequestsKeepTheSameVocalPitch() {
        listOf("estoy triste", "hablá más rápido", "hablá más despacio", "qué hora es").forEach {
            assertEquals(SpeechProsody().pitch, SpeechProsody.forInput(it).pitch, 0f)
        }
    }
}
