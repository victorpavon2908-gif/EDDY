package com.niko.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NikoUiModeStorePhase3Test {
    @Test fun resolvesEveryEmbeddedModeName() {
        val cases = listOf(
            "cámara" to NikoUiMode.CAMERA,
            "grabadora de video" to NikoUiMode.VIDEO,
            "grabadora de audio" to NikoUiMode.AUDIO_RECORDER,
            "reproductor de música" to NikoUiMode.MUSIC,
            "calculadora" to NikoUiMode.CALCULATOR,
            "cronómetro" to NikoUiMode.STOPWATCH,
            "cuenta regresiva" to NikoUiMode.TIMER,
            "reloj" to NikoUiMode.CLOCK,
            "bloc de notas" to NikoUiMode.NOTES,
            "conversor de unidades" to NikoUiMode.CONVERTER,
            "herramientas" to NikoUiMode.TOOLBOX,
            "herramienta generada" to NikoUiMode.GENERATED,
            "pantalla principal" to NikoUiMode.ASSISTANT,
        )
        cases.forEach { (spoken, expected) ->
            assertEquals(spoken, expected, NikoUiModeStore.resolve(spoken))
        }
    }

    @Test fun resolvesVoiceDiagnosticsFromNaturalSpanish() {
        assertEquals(NikoUiMode.VOICE_DIAGNOSTICS, NikoUiModeStore.resolve("diagnóstico de voz"))
        assertEquals(NikoUiMode.VOICE_DIAGNOSTICS, NikoUiModeStore.resolve("prueba del wake word"))
        assertEquals(NikoUiMode.VOICE_DIAGNOSTICS, NikoUiModeStore.resolve("diagnostico del microfono"))
    }
}
