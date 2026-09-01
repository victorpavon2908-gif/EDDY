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
    fun conversationalModelIsRuntimeDownloadNotAcousticDependency() {
        assertTrue(NikoModelCatalog.localLlm.url.startsWith("https://"))
        assertTrue(NikoModelCatalog.localLlm.minBytes >= 400_000_000L)
        assertTrue(NikoModelCatalog.localLlm !in NikoModelCatalog.acousticCore)
    }
}
