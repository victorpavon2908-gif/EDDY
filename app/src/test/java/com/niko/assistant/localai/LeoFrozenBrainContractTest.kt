package com.niko.assistant.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeoFrozenBrainContractTest {
    @Test fun storageContractIsExactlyFiveDecimalGigabytes() {
        assertEquals(500_000_000L, LeoBrainStorage.FROZEN_TARGET_BYTES)
        assertEquals(4_500_000_000L, LeoBrainStorage.ADAPTIVE_MAX_BYTES)
        assertEquals(
            LeoBrainStorage.FROZEN_TARGET_BYTES + LeoBrainStorage.ADAPTIVE_MAX_BYTES,
            LeoBrainStorage.TOTAL_MAX_BYTES,
        )
        assertTrue(LeoBrainStorage.MIN_DEVICE_FREE_BYTES > 0L)
    }

    @Test fun releaseManifestMustDescribeTheFrozenBrain() {
        val manifest = LeoFrozenBrainManager.parseManifest(
            """{
              "schema":1,
              "version":"leo-brain-v1",
              "archive_name":"leo-brain-v1.zip",
              "archive_bytes":210000000,
              "archive_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "installed_bytes":496000000,
              "installed_sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
            }""",
        )
        assertEquals(LeoFrozenBrainManager.BRAIN_VERSION, manifest.version)
        assertEquals(496_000_000L, manifest.installedBytes)
        assertEquals(210_000_000L, manifest.archiveBytes)
    }

    @Test fun frozenRetrievalDropsConversationalStopWordsAndBuildsSafeFtsQueries() {
        val terms = LeoFrozenKnowledgeStore.queryTerms("Leo, ¿qué es la inteligencia artificial y cómo funciona?")
        assertEquals(listOf("inteligencia", "artificial", "funciona"), terms)
        val andQuery = LeoFrozenKnowledgeStore.matchExpression(terms, andMode = true)
        val orQuery = LeoFrozenKnowledgeStore.matchExpression(terms, andMode = false)
        assertEquals("\"inteligencia\" \"artificial\" \"funciona\"", andQuery)
        assertTrue(orQuery.contains(" OR "))
        assertFalse(andQuery.contains("?"))
    }

    @Test fun currentReleaseUrlsPointToGithubReleaseAssets() {
        assertTrue(LeoFrozenBrainManager.MANIFEST_URL.startsWith("https://github.com/"))
        assertTrue(LeoFrozenBrainManager.ARCHIVE_URL.startsWith("https://github.com/"))
        assertTrue(LeoFrozenBrainManager.MANIFEST_URL.contains(LeoFrozenBrainManager.BRAIN_VERSION))
        assertTrue(LeoFrozenBrainManager.ARCHIVE_URL.endsWith(LeoFrozenBrainManager.ARCHIVE_NAME))
    }
}
