package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class ProgressiveSpeechTest {
    @Test fun firstSentenceIsReadyBeforeGenerationEnds() {
        val speech = ProgressiveSpeech()
        speech.append("Hola, aquí estoy. ")
        assertEquals("Hola, aquí estoy.", speech.poll())
        assertFalse(speech.isDrained)
        speech.append("¿Qué necesitás?")
        speech.finish()
        assertEquals("¿Qué necesitás?", speech.poll())
        assertTrue(speech.isDrained)
    }
    @Test fun characterDeltasPreserveDecimalsAccentsAndOrder() {
        val speech = ProgressiveSpeech()
        val text = "Cuesta 3.50 dólares. Hablá con el Sr. Pérez. ¿Todo bien?"
        text.forEach { speech.append(it.toString()) }
        speech.finish()
        val output = generateSequence { speech.poll() }.toList()
        assertEquals(listOf("Cuesta 3.50 dólares.", "Hablá con el Sr. Pérez.", "¿Todo bien?"), output)
    }
    @Test fun longAnswerWithoutPunctuationStartsBeforeFinish() {
        val speech = ProgressiveSpeech()
        speech.append("palabra ".repeat(20))
        assertNotNull(speech.poll())
        assertFalse(speech.isDrained)
    }
    @Test fun cancelDiscardsPendingAndLateTokens() {
        val speech = ProgressiveSpeech()
        speech.append("Primera oración. Segunda oración incompleta")
        speech.cancel()
        speech.append(" más texto.")
        speech.finish()
        assertNull(speech.poll())
        assertTrue(speech.isDrained)
    }
    @Test fun finishNeverRepeatsLastPhrase() {
        val speech = ProgressiveSpeech()
        speech.append("Una respuesta corta")
        speech.finish()
        speech.finish()
        assertEquals("Una respuesta corta", speech.poll())
        assertNull(speech.poll())
    }
}
