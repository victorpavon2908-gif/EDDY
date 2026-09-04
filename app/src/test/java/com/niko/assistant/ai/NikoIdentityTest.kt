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
        assertNull(NikoIdentity.replyTo("quién creó WhatsApp"))
        assertNull(NikoIdentity.replyTo("cómo funciona la memoria RAM"))
        assertNull(NikoIdentity.replyTo("explicame cómo funciona la memoria de una computadora"))
        assertFalse(NikoIdentity.isLearningQuestion("cómo funciona la memoria RAM"))
    }

    @Test fun developerIdentityIsCanonicalAndLocal() {
        for (question in listOf("¿Quién es tu desarrollador?", "Leo, quién te creó", "cómo se llama tu creador", "quién es el desarrollador de Leo")) {
            assertTrue(NikoIdentity.replyTo(question).orEmpty().contains("Víctor Pavón"))
        }
    }

    @Test fun explainsLocalLearningWithoutClaimingContinuousModelTraining() {
        val enabled = NikoIdentity.replyTo("¿Te entrenás conforme interactuás conmigo?", true, 27, 3).orEmpty()
        assertTrue(enabled.contains("Entreno de verdad"))
        assertTrue(enabled.contains("27 actualizaciones"))
        assertTrue(enabled.contains("3 correcciones"))
        assertTrue(enabled.contains("No reentreno el modelo generativo completo"))
        assertTrue(NikoIdentity.isLearningQuestion("se supone que Leo se va a entrenar conforme interactúa con migo"))
        assertTrue(NikoIdentity.isLearningQuestion("lo que quiero es que en realidad él se entrene conforme va estando con migo"))

        val disabled = NikoIdentity.replyTo("¿Vas aprendiendo de mí?", false).orEmpty()
        assertTrue(disabled.contains("desactivado"))
        assertTrue(disabled.contains("Ajustes"))
    }

    @Test fun speechMigratesRetiredBrandWithoutChangingOtherWords() {
        assertEquals("Soy Leo. Nicaragua es mi contexto.", NikoIdentity.forSpeech("Soy NIKO. Nicaragua es mi contexto."))
        assertEquals("Leo está listo.", NikoIdentity.forSpeech("Nico está listo."))
    }
}
