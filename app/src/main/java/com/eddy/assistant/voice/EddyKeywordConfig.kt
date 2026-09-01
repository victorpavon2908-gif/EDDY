package com.eddy.assistant.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/** File-based JNI construction validates keywords BEFORE createStream can be called. */
object EddyKeywordConfig {
    fun create(modelDirectory: File, configDirectory: File): KeywordSpotterConfig {
        val keywords = prepareKeywords(configDirectory)
        val root = File(modelDirectory, "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20")
        return KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = 16_000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = File(root, "encoder-epoch-13-avg-2-chunk-8-left-64.int8.onnx").absolutePath,
                    decoder = File(root, "decoder-epoch-13-avg-2-chunk-8-left-64.onnx").absolutePath,
                    joiner = File(root, "joiner-epoch-13-avg-2-chunk-8-left-64.int8.onnx").absolutePath,
                ),
                tokens = File(root, "tokens.txt").absolutePath,
                numThreads = 1,
                provider = "cpu",
                modelType = "zipformer2",
                modelingUnit = "phone+ppinyin",
            ),
            maxActivePaths = 4,
            keywordsFile = keywords.absolutePath,
            keywordsScore = 3.0f,
            keywordsThreshold = 0.08f,
            numTrailingBlanks = 1,
        )
    }

    private fun prepareKeywords(directory: File): File {
        check(directory.isDirectory || directory.mkdirs()) { "No pude crear la configuración de activación." }
        val destination = File(directory, "eddy-keywords.txt")
        if (destination.isFile && destination.readText() == KEYWORDS) return destination
        val temporary = File.createTempFile("eddy-keywords-", ".tmp", directory)
        try {
            temporary.writeText(KEYWORDS, Charsets.UTF_8)
            check(temporary.renameTo(destination)) { "No pude guardar las palabras de activación." }
        } finally { temporary.delete() }
        return destination
    }

    private const val KEYWORDS =
        "EH1 D IY0 :3.2 #0.07 @EDDY\n" +
        "EH1 D IY1 :3.2 #0.07 @EDDY\n" +
        "EH0 D IY0 :3.0 #0.08 @EDDY\n" +
        "EH0 D IY1 :3.0 #0.08 @EDDY\n" +
        "EH1 D IH0 :2.8 #0.10 @EDDY\n"
}
