package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class NikoIdentityTest {
    @Test fun identityUsesLeoWithoutACloudCall() {
        for (question in listOf("¿Cómo te llamás?", "quién sos", "Leo, ¿cuál es tu nombre?")) {
            assertTrue(NikoIdentity.replyTo(question).orEmpty().startsWith("Soy Leo,"))
        }
    }

    @Test fun oldNameInAQuestionStillReturnsCurrentIdentity() {
        assertTrue(NikoIdentity.replyTo("Nico, ¿cómo te llamás?").orEmpty().startsWith("Soy Leo,"))
    }

    @Test fun identityDoesNotReplaceQuestionsAboutOtherPeopleOrExplicitLessons() {
        assertNull(NikoIdentity.replyTo("cómo se llama mi hijo"))
        assertNull(NikoIdentity.replyTo("cuando te pregunte mi nombre, respondé Manuel"))
    }

    @Test fun speechMigratesRetiredBrandWithoutChangingOtherWords() {
        assertEquals("Soy Leo. Nicaragua es mi contexto.", NikoIdentity.forSpeech("Soy NIKO. Nicaragua es mi contexto."))
        assertEquals("Leo está listo.", NikoIdentity.forSpeech("Nico está listo."))
    }
}
