package com.niko.assistant.localai

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LeoFrozenReleaseCacheTest {
    @Test fun onlyValidatedReleaseMetadataCanBeUsedForInterruptedRecovery() {
        val app = RuntimeEnvironment.getApplication()
        val manager = LeoFrozenBrainManager(app)
        val manifest = LeoFrozenBrainManager.ReleaseManifest(LeoFrozenBrainManager.BRAIN_VERSION,
            LeoFrozenBrainManager.ARCHIVE_NAME, 210000000, "a".repeat(64), 496000000, "b".repeat(64))
        manager.cacheTrustedManifest(manifest)
        assertEquals(manifest, manager.cachedTrustedManifest())
        assertTrue(runCatching { manager.cacheTrustedManifest(manifest.copy(installedSha256 = "unknown")) }.isFailure)
        assertEquals(manifest, manager.cachedTrustedManifest())
        File(LeoBrainStorage(app).downloads, "trusted-frozen-release.json").writeText("invalid")
        assertNull(manager.cachedTrustedManifest())
    }
}
