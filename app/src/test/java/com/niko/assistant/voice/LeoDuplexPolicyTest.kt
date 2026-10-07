package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class LeoDuplexPolicyTest {
    @Test fun wakeAndStopsInterrupt() {
        listOf("LEO", "Leo pará", "Oye Leo mejor buscá otra cosa", "pará", "No, eso no").forEach {
            assertTrue(it, LeoDuplexPolicy.canInterrupt(it, "El reporte está casi listo"))
        }
    }
    @Test fun responseEchoDoesNotInterrupt() {
        val speech = "Soy LEO, para continuar decime qué necesitás"
        listOf("LEO", "para", "para continuar", "Soy LEO").forEach {
            assertFalse(it, LeoDuplexPolicy.canInterrupt(it, speech))
        }
    }
    @Test fun nonCommandsAndSimilarNamesDoNotInterrupt() {
        listOf("galileo", "leon", "empleo", "abrí la cámara", "el texto dice leo pará").forEach {
            assertFalse(it, LeoDuplexPolicy.canInterrupt(it, ""))
        }
    }
    @Test fun echoUsesWholeWordsAndIgnoresCaseAndPunctuation() {
        assertTrue(LeoDuplexPolicy.isEcho("¿LEO, pará?", "Leo pará. Luego seguimos."))
        assertFalse(LeoDuplexPolicy.isEcho("leo", "Galileo mira el cielo"))
        assertFalse(LeoDuplexPolicy.isEcho("", "Texto"))
    }
    @Test fun oldPlaybackCannotClearNewReference() {
        LeoRealtimeTurnBus.beginSpeechReference("old", "viejo")
        LeoRealtimeTurnBus.beginSpeechReference("new", "respuesta nueva")
        LeoRealtimeTurnBus.endSpeechReference("old")
        assertEquals("respuesta nueva", LeoRealtimeTurnBus.spokenReference())
        LeoRealtimeTurnBus.endSpeechReference("new")
    }
}
