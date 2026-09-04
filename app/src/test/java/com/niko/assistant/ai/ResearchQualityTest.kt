package com.niko.assistant.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchQualityTest {
    @Test fun prefersIndependentlySourcedDepthAndNeverMergesClaims() {
        val native = reply("Resumen local suficientemente detallado.", "a.example", "b.example")
        val compound = reply("Respuesta asistida con contexto, fechas y explicación más extensa para el usuario.", "a.example", "b.example", "c.example")

        assertEquals(compound, ResearchQuality.choose(native, compound))
    }

    @Test fun keepsNativeEvidenceWhenCloudHasNoValidatedSources() {
        val native = reply("Respuesta local.", "a.example", "b.example")
        val unsupported = NikoAiReply("Texto sin evidencia.", false, emptyList())

        assertEquals(native, ResearchQuality.choose(native, unsupported))
        assertEquals(native, ResearchQuality.choose(native, null))
    }

    private fun reply(text: String, vararg hosts: String) = NikoAiReply(
        text = text,
        webUsed = true,
        sources = hosts.mapIndexed { index, host -> NikoWebSource("Fuente ${index + 1}", "https://$host/page") },
    )
}
