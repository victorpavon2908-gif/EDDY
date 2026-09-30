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
    @Test fun largeSingleDeltaProducesBoundedOrderedPhrases() {
        val speech = ProgressiveSpeech()
        val text = "palabra ".repeat(700).trim() + "."
        speech.append(text)
        speech.finish()
        val phrases = generateSequence { speech.poll() }.toList()
        assertTrue(phrases.size > 10)
        assertTrue(phrases.all { it.length <= 240 })
        assertEquals(text, phrases.joinToString(" "))
    }
    @Test fun hugeTokenCannotBlockFirstAudioOrSplitEmoji() {
        val speech = ProgressiveSpeech()
        val text = "x".repeat(239) + "😀" + "y".repeat(500)
        speech.append(text)
        val first = speech.poll()
        assertNotNull(first)
        speech.finish()
        val rest = generateSequence { speech.poll() }.toList()
        assertEquals(text, first + rest.joinToString(""))
        assertFalse(first!!.last().isHighSurrogate())
        assertTrue(rest.all { it.length <= 240 })
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
