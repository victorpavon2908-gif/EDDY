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
    fun qualityConversationModelIsQwen15BForAndroid() {
        val model = NikoModelCatalog.localLlmQuality
        assertTrue(model.url.contains("Qwen2.5-1.5B-Instruct"))
        assertTrue(model.expectedFiles.single().contains("q8_ekv1280.task"))
        assertTrue(model.minBytes >= 1_400_000_000L)
        assertTrue(model !in NikoModelCatalog.acousticCore)
    }

    @Test
    fun fastConversationModelRemainsAvailableAsFallback() {
        val model = NikoModelCatalog.localLlmFast
        assertTrue(model.url.contains("Qwen2.5-0.5B-Instruct"))
        assertTrue(model.minBytes >= 400_000_000L)
        assertTrue(model !in NikoModelCatalog.acousticCore)
        assertTrue(NikoModelCatalog.conversationModels.containsAll(listOf(NikoModelCatalog.localLlmQuality, model)))
    }

    @Test
    fun firstRunBundleContainsEveryVoiceModuleBeforeLeoStarts() {
        val models = NikoModelCatalog.firstRunModels(balancedProfile())
        assertTrue(models.containsAll(NikoModelCatalog.voiceCore))
        assertTrue(NikoModelCatalog.speaker in models)
        assertTrue(NikoModelCatalog.spanishVoice in models)
        assertTrue(NikoModelCatalog.whisperAsr in models)
        assertTrue(NikoModelCatalog.localLlmFast in models)
        assertEquals(models.size, models.distinctBy { it.id }.size)
    }

    @Test
    fun powerDevicesPrepareQualityBrainAndFastRecoveryBrain() {
        val profile = NikoDeviceProfile(
            tier = NikoDeviceProfile.Tier.POWER,
            totalRamMb = 8_192,
            cpuCores = 8,
            inferenceThreads = 4,
            abi = "arm64-v8a",
        )
        val models = NikoModelCatalog.firstRunModels(profile)
        assertTrue(NikoModelCatalog.localLlmQuality in models)
        assertTrue(NikoModelCatalog.localLlmFast in models)
        assertTrue(models.indexOf(NikoModelCatalog.localLlmQuality) < models.indexOf(NikoModelCatalog.voiceCore.first()))
    }

    @Test
    fun liteDevicesDoNotDownloadAnUnsafeLocalLlm() {
        val profile = NikoDeviceProfile(
            tier = NikoDeviceProfile.Tier.LITE,
            totalRamMb = 3_000,
            cpuCores = 4,
            inferenceThreads = 1,
            abi = "arm64-v8a",
        )
        val models = NikoModelCatalog.firstRunModels(profile)
        assertFalse(NikoModelCatalog.localLlmFast in models)
        assertFalse(NikoModelCatalog.localLlmQuality in models)
        assertTrue(models.containsAll(NikoModelCatalog.voiceCore))
        assertTrue(NikoModelCatalog.spanishVoice in models)
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
