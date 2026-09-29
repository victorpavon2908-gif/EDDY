package com.niko.assistant.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceControlTest {
    @Test fun stopsAndDeactivationAreDifferentCommands() {
        listOf("Leo, pará", "Leo para.", "pará por favor", "Leo, dejá de hablar", "Leo, yo quiero que pares").forEach {
            assertEquals(it, VoiceControl.STOP, VoiceControl.parse(it))
        }
        listOf("Leo, desactívate", "Leo, apagate", "Leo, quiero que te desactives", "desactiva la escucha").forEach {
            assertEquals(it, VoiceControl.DEACTIVATE, VoiceControl.parse(it))
        }
    }
    @Test fun messageContentsNegationAndOtherDevicesCannotDisableLeo() {
        listOf("mandá por WhatsApp diciendo Leo para", "no te desactives", "desactiva el bluetooth",
            "para qué sirve", "buscá cómo desactivarte", "explicame cómo parar", "apaga la luz").forEach {
            assertNull(it, VoiceControl.parse(it))
        }
    }
}
