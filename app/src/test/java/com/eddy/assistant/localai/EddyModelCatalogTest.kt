package com.eddy.assistant.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EddyModelCatalogTest {
    @Test
    fun acousticCoreUsesUniqueHttpsModels() {
        val models = EddyModelCatalog.acousticCore
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
        assertTrue(EddyModelCatalog.localLlm.url.startsWith("https://"))
        assertTrue(EddyModelCatalog.localLlm.minBytes >= 400_000_000L)
        assertTrue(EddyModelCatalog.localLlm !in EddyModelCatalog.acousticCore)
    }
}
