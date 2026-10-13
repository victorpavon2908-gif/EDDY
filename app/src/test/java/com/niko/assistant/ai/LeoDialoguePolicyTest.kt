package com.niko.assistant.ai

import org.junit.Assert.*
import org.junit.Test

class LeoDialoguePolicyTest {
    @Test fun clarifiesOnlyIncompleteRequests() {
        assertNotNull(LeoDialoguePolicy.clarification("Ayudame"))
        assertNotNull(LeoDialoguePolicy.clarification("Explicame este error"))
        assertNull(LeoDialoguePolicy.clarification("Ayudame a cocinar arroz"))
        assertNull(LeoDialoguePolicy.clarification("Explicame este error: variable sin definir"))
        assertNull(LeoDialoguePolicy.clarification("Abrí Spotify"))
    }
    @Test fun ambiguousActionsNeverInheritAuthorizationFromOldAnswer() {
        assertNotNull(LeoDialoguePolicy.clarification("Mandalo", "Mensaje preparado"))
        assertNotNull(LeoDialoguePolicy.clarification("Bórralo", "Tengo dos documentos"))
        assertNotNull(LeoDialoguePolicy.clarification("Sí"))
        assertNull(LeoDialoguePolicy.clarification("Sí", "¿Querés continuar?"))
    }
    @Test fun styleIsExplicitAndCanBeReplaced() {
        assertEquals("brief", LeoDialoguePolicy.style("Leo, respondeme más corto"))
        assertEquals("detailed", LeoDialoguePolicy.style("Prefiero respuestas detalladas"))
        assertNull(LeoDialoguePolicy.style("¿A ella le gustan las respuestas cortas?"))
    }
    @Test fun shortNarrationPreservesFullAnswerAndDoesNotCutSentences() {
        val original = "Primera explicación. Segunda explicación. Tercera explicación. " + "Un detalle adicional. ".repeat(30)
        val voice = LeoDialoguePolicy.researchSpeech(original, "brief")
        assertTrue(voice.startsWith("Primera explicación. Segunda explicación. Tercera explicación."))
        assertTrue(voice.endsWith("El detalle completo queda en pantalla."))
        assertEquals(original, LeoDialoguePolicy.researchSpeech(original, "detailed"))
        assertEquals("Correcto.", LeoDialoguePolicy.researchSpeech("Correcto.", "brief"))
    }
    @Test fun capabilitiesStayLocalAndExplainLimits() {
        for (input in listOf("¿Qué sabés hacer?", "LEO, qué podés hacer", "qué saves hacer")) {
            val answer = NikoIdentity.replyTo(input).orEmpty()
            assertTrue(answer.contains("Puedo buscar"))
            assertTrue(answer.contains("permisos"))
            assertTrue(answer.contains("no puedo convertirme en cualquier"))
        }
        assertNull(NikoIdentity.replyTo("qué puede hacer una impresora"))
    }
}
