package com.niko.assistant.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteractionCorrectionTest {
    @Test fun extractsNaturalNicaraguanCorrectionsWithoutChangingOrdinaryNegations() {
        assertEquals("abrime WhatsApp", InteractionCorrection.correctedText("Leo, no, quise decir abrime WhatsApp"))
        assertEquals("encendé la linterna", InteractionCorrection.correctedText("te dije que encendé la linterna"))
        assertEquals("buscá noticias de Nicaragua", InteractionCorrection.correctedText("me refería a buscá noticias de Nicaragua"))
        assertNull(InteractionCorrection.correctedText("no abras WhatsApp"))
        assertNull(InteractionCorrection.correctedText("no quise decir abrime WhatsApp"))
        assertNull(InteractionCorrection.correctedText("explicame qué quise decir"))
    }
}
