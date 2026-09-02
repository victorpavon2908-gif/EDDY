package com.niko.assistant.localai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NikoDeviceProfileCompatibilityTest {

    @Test
    fun honorDisablesMediaPipeLocalLlmEvenWithEnoughRamAndCores() {
        val profile = capableProfile("HONOR")

        assertFalse(profile.localLlmRuntimeSafe)
        assertFalse(profile.supportsLocalLlm)
        assertFalse(profile.prefersQualityLocalLlm)
    }

    @Test
    fun honorFirstRunDoesNotDownloadQwenModels() {
        val models = NikoModelCatalog.firstRunModels(capableProfile("HONOR"))

        assertTrue(models.none { it in NikoModelCatalog.conversationModels })
        assertTrue(models.containsAll(NikoModelCatalog.voiceCore))
        assertTrue(models.contains(NikoModelCatalog.spanishAsr))
        assertTrue(models.contains(NikoModelCatalog.spanishVoice))
    }

    @Test
    fun otherCapableManufacturersKeepLocalLlmAvailable() {
        val profile = capableProfile("samsung")

        assertTrue(profile.localLlmRuntimeSafe)
        assertTrue(profile.supportsLocalLlm)
        assertTrue(profile.prefersQualityLocalLlm)
        assertTrue(NikoModelCatalog.firstRunModels(profile).contains(NikoModelCatalog.localLlmQuality))
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
