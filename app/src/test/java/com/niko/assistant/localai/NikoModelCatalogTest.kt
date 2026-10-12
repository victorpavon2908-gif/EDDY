package com.niko.assistant.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun whisperRemainsSecondaryButIsPreparedBeforeFirstNormalStart() {
        val model = NikoModelCatalog.whisperAsr
        assertTrue(model.url.contains("sherpa-onnx-whisper-tiny.tar.bz2"))
        assertTrue(model.expectedFiles.any { it.endsWith("tiny-encoder.int8.onnx") })
        assertTrue(model.expectedFiles.any { it.endsWith("tiny-decoder.int8.onnx") })
        assertTrue(model in NikoModelCatalog.advancedVoice)
        assertTrue(model !in NikoModelCatalog.voiceCore)

        val profile = balancedProfile()
        assertTrue(model in NikoModelCatalog.firstRunModels(profile))
    }

    @Test
    fun firstRunBundleContainsEveryVoiceModuleButNoInProcessLlm() {
        val models = NikoModelCatalog.firstRunModels(balancedProfile())
        assertTrue(models.containsAll(NikoModelCatalog.voiceCore))
        assertFalse(models.any { it.id.startsWith("llm-") || it.id.startsWith("tts-") })
        assertTrue(NikoModelCatalog.speaker in models)
        assertTrue(NikoModelCatalog.whisperAsr in models)
        assertEquals(models.size, models.distinctBy { it.id }.size)
    }

    @Test
    fun powerDevicesAlsoUseUniversalSafeCoreWithoutQwen() {
        val profile = NikoDeviceProfile(
            tier = NikoDeviceProfile.Tier.POWER,
            totalRamMb = 8_192,
            cpuCores = 8,
            inferenceThreads = 4,
            abi = "arm64-v8a",
            manufacturer = "generic",
        )
        val models = NikoModelCatalog.firstRunModels(profile)
        assertTrue(models.containsAll(NikoModelCatalog.voiceCore))
        assertFalse(models.any { it.id.startsWith("llm-") || it.id.startsWith("tts-") })
        assertTrue(NikoModelCatalog.speaker in models)
        assertTrue(NikoModelCatalog.whisperAsr in models)
    }

    @Test
    fun liteDevicesUseTheSameSafeVoiceCore() {
        val profile = NikoDeviceProfile(
            tier = NikoDeviceProfile.Tier.LITE,
            totalRamMb = 3_000,
            cpuCores = 4,
            inferenceThreads = 1,
            abi = "arm64-v8a",
        )
        val models = NikoModelCatalog.firstRunModels(profile)
        assertTrue(models.containsAll(NikoModelCatalog.voiceCore))
        assertFalse(models.any { it.id.startsWith("llm-") || it.id.startsWith("tts-") })
        assertTrue(NikoModelCatalog.whisperAsr in models)
    }

    private fun balancedProfile() = NikoDeviceProfile(
        tier = NikoDeviceProfile.Tier.BALANCED,
        totalRamMb = 5_000,
        cpuCores = 6,
        inferenceThreads = 2,
        abi = "arm64-v8a",
    )
}
