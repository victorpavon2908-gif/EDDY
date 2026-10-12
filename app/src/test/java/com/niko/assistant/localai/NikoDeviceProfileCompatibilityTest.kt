package com.niko.assistant.localai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoDeviceProfileCompatibilityTest {

    @Test
    fun allManufacturersUseSameSafeCorePolicy() {
        listOf("HONOR", "Samsung", "Xiaomi", "Motorola", "OPPO", "vivo", "Google", "OnePlus").forEach { manufacturer ->
            val profile = capableProfile(manufacturer)
            val models = NikoModelCatalog.firstRunModels(profile)
            assertTrue("$manufacturer must retain all voice inputs", models.containsAll(NikoModelCatalog.voiceCore))
            assertFalse("$manufacturer must not download unused inference models",
                models.any { it.id.startsWith("llm-") || it.id.startsWith("tts-") })
        }
    }

    @Test
    fun firstRunNeverDownloadsQwenForUniversalCore() {
        listOf("HONOR", "Samsung", "Xiaomi", "Google").forEach { manufacturer ->
            val models = NikoModelCatalog.firstRunModels(capableProfile(manufacturer))
            assertTrue(models.none { it.id.startsWith("llm-") || it.id.startsWith("tts-") })
            assertTrue(models.containsAll(NikoModelCatalog.voiceCore))
            assertTrue(models.contains(NikoModelCatalog.spanishAsr))
        }
    }

    @Test
    fun voiceCoreRemainsIndependentFromLocalLlm() {
        val profile = capableProfile("generic")
        val models = NikoModelCatalog.firstRunModels(profile)

        assertTrue(models.containsAll(NikoModelCatalog.voiceCore))
        assertTrue(models.contains(NikoModelCatalog.speaker))
        assertTrue(models.contains(NikoModelCatalog.whisperAsr))
        assertTrue(models.none { it.id.startsWith("llm-") })
    }

    private fun capableProfile(manufacturer: String) = NikoDeviceProfile(
        tier = NikoDeviceProfile.Tier.POWER,
        totalRamMb = 8_192L,
        cpuCores = 8,
        inferenceThreads = 4,
        abi = "arm64-v8a",
        manufacturer = manufacturer,
        model = "test-device",
    )
}
