package com.niko.assistant.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptQualityTest {
    @Test fun blankTranscriptRequestsWhisperRefinement() {
        assertTrue(TranscriptQuality.shouldRefine("", 16_000))
    }

    @Test fun cleanNaturalCommandStaysOnFastCanaryPath() {
        assertFalse(TranscriptQuality.shouldRefine("abrí YouTube", 32_000))
        assertFalse(TranscriptQuality.shouldRefine("apagála", 20_000))
    }

    @Test fun artifactsAndImplausiblyFastOutputRequestRefinement() {
        assertTrue(TranscriptQuality.shouldRefine("hola ".repeat(40), 32_000))
        assertTrue(TranscriptQuality.shouldRefine("<unk>", 8_000))
    }

    @Test fun intentionalRepetitionsAndDictatedNamesArePreserved() {
        assertFalse(TranscriptQuality.shouldRefine("no no no, Nico", 32_000))
        assertEquals("hola hola hola hola", TranscriptQuality.choose("hola hola hola hola", "hola"))
        assertEquals("Escribí a Nico: ¡Qué tuani!", TranscriptQuality.choose("Escribí a Nico: ¡Qué tuani!", ""))
    }

    @Test fun longAudioWithMissingWordsGetsAnotherPass() {
        assertTrue(TranscriptQuality.shouldRefine("WhatsApp", 16_000 * 4))
        assertEquals("abrí WhatsApp y buscá a Victor",
            TranscriptQuality.choose("WhatsApp", "abrí WhatsApp y buscá a Victor", 16_000 * 4))
        assertEquals("enviá mensaje", TranscriptQuality.choose("enviá mensaje", "mensaje enviá a alguien", 16_000 * 7))
    }

    @Test fun conflictingNegationsNumbersOrMeaningMustBeRepeated() {
        assertTrue(TranscriptQuality.requiresClarification("no abras WhatsApp", "abre WhatsApp", 32_000))
        assertTrue(TranscriptQuality.requiresClarification("llamá al 88887777", "llamá al 88887778", 32_000))
        assertTrue(TranscriptQuality.requiresClarification("volumen al veinte", "volumen al treinta", 32_000))
        assertTrue(TranscriptQuality.requiresClarification("cerrá WhatsApp", "abrí la cámara", 32_000))
        assertFalse(TranscriptQuality.requiresClarification("¡Abrí WhatsApp!", "abrí WhatsApp", 32_000))
        assertFalse(TranscriptQuality.requiresClarification("<unk>", "abrí WhatsApp", 32_000))
    }

    @Test fun refinementWinsWhenPrimaryContainsArtifacts() {
        assertEquals(
            "prendé la linterna",
            TranscriptQuality.choose("<unk>", "prendé la linterna"),
        )
    }
}
