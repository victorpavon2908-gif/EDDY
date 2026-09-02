package com.niko.assistant.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoModelCatalogTest {
    @Test
    fun acousticCoreUsesUniqueHttpsModels() {
        val models = NikoModelCatalog.acousticCore
        assertEquals(models.size, models.map { it.id }.distinct().size)
        models.forEach { spec ->
            assertTrue(spec.url.startsWith("https://"))
            assertTrue(spec.expectedFiles.isNotEmpty())
            assertTrue(spec.expectedFiles.none { it.startsWith("/") || it.contains("..") })
            assertTrue(spec.minBytes > 0)
        }
    }

    @Test
    fun spanishRecognitionUsesLocalCanaryInt8() {
        val model = NikoModelCatalog.spanishAsr
        assertTrue(model.id.contains("canary"))
        assertTrue(model.url.contains("nemo-canary-180m-flash"))
        assertTrue(model.expectedFiles.any { it.endsWith("encoder.int8.onnx") })
        assertTrue(model.expectedFiles.any { it.endsWith("decoder.int8.onnx") })
        assertTrue(model in NikoModelCatalog.voiceCore)
    }

    @Test
    fun conversationalModelIsRuntimeDownloadNotAcousticDependency() {
        assertTrue(NikoModelCatalog.localLlm.url.startsWith("https://"))
        assertTrue(NikoModelCatalog.localLlm.minBytes >= 400_000_000L)
        assertTrue(NikoModelCatalog.localLlm !in NikoModelCatalog.acousticCore)
    }
}
