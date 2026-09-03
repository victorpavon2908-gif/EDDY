package com.niko.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class NikoUiModeStorePhase3Test {
    @Test fun resolvesVoiceDiagnosticsFromNaturalSpanish() {
        assertEquals(NikoUiMode.VOICE_DIAGNOSTICS, NikoUiModeStore.resolve("diagnóstico de voz"))
        assertEquals(NikoUiMode.VOICE_DIAGNOSTICS, NikoUiModeStore.resolve("prueba del wake word"))
        assertEquals(NikoUiMode.VOICE_DIAGNOSTICS, NikoUiModeStore.resolve("diagnostico del microfono"))
    }
}
