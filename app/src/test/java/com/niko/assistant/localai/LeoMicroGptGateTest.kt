package com.niko.assistant.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeoMicroGptGateTest {
    @Test fun difficultParaphrasesRouteToTheTrainedFamily() {
        assertEquals(LeoMicroGptGate.Family.HOW_ARE_YOU, LeoMicroGptGate.classify("qué onda mae cómo andás"))
        assertEquals(LeoMicroGptGate.Family.DONT_INVENT, LeoMicroGptGate.classify("Leo no me inventés nada porfa"))
        assertEquals(LeoMicroGptGate.Family.OFFLINE, LeoMicroGptGate.classify("podés seguir sin wifi"))
        assertEquals(LeoMicroGptGate.Family.CONTINUE, LeoMicroGptGate.classify("seguí con lo que estábamos viendo"))
        assertEquals(LeoMicroGptGate.Family.NATURAL, LeoMicroGptGate.classify("quiero que hablés más natural"))
        assertEquals(LeoMicroGptGate.Family.VERIFY_CURRENT, LeoMicroGptGate.classify("si un dato es de hoy verificá primero"))
        assertEquals(LeoMicroGptGate.Family.MODEL, LeoMicroGptGate.classify("qué micro modelo tenés adentro"))
        assertEquals(LeoMicroGptGate.Family.TIRED, LeoMicroGptGate.classify("ando cansadísimo hoy"))
        assertEquals(LeoMicroGptGate.Family.ORGANIZE, LeoMicroGptGate.classify("quiero ordenar mis pendientes"))
        assertEquals(LeoMicroGptGate.Family.CAPABILITIES, LeoMicroGptGate.classify("qué cosas sabés hacer"))
        assertEquals(LeoMicroGptGate.Family.THANKS, LeoMicroGptGate.classify("gracias por ayudarme"))
    }

    @Test fun openDomainFactsStayOutsideTheTinyModel() {
        assertNull(LeoMicroGptGate.classify("qué es la fotosíntesis"))
        assertNull(LeoMicroGptGate.classify("cuál es la capital de Mongolia"))
        assertNull(LeoMicroGptGate.classify("quién ganó las elecciones de ayer"))
        assertNull(LeoMicroGptGate.classify("abrime WhatsApp y llamá a Carlos"))
    }
}
