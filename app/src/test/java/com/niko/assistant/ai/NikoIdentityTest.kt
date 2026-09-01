package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class NikoIdentityTest {
    @Test fun identityUsesTheCurrentNameWithoutACloudCall() {
        for (question in listOf("¿Cómo te llamás?", "quién sos", "Nico, ¿cuál es tu nombre?")) {
            assertTrue(NikoIdentity.replyTo(question).orEmpty().startsWith("Soy Niko,"))
        }
    }

    @Test fun identityDoesNotReplaceQuestionsAboutOtherPeopleOrExplicitLessons() {
        assertNull(NikoIdentity.replyTo("cómo se llama mi hijo"))
        assertNull(NikoIdentity.replyTo("cuando te pregunte mi nombre, respondé Manuel"))
    }

    @Test fun speechUsesSpanishPronunciationWithoutChangingOtherWords() {
        assertEquals("Soy Nico. Nicaragua es mi contexto.", NikoIdentity.forSpeech("Soy NIKO. Nicaragua es mi contexto."))
    }
}
