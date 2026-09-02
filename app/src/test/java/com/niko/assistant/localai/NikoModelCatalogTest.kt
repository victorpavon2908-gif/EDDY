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
    fun whisperIsOptionalMultilingualInt8Refinement() {
        val model = NikoModelCatalog.whisperAsr
        assertTrue(model.url.contains("sherpa-onnx-whisper-tiny.tar.bz2"))
        assertTrue(model.expectedFiles.any { it.endsWith("tiny-encoder.int8.onnx") })
        assertTrue(model.expectedFiles.any { it.endsWith("tiny-decoder.int8.onnx") })
        assertTrue(model in NikoModelCatalog.advancedVoice)
        assertTrue(model !in NikoModelCatalog.voiceCore)
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
}
