package com.niko.assistant.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/** File-based JNI construction validates keywords BEFORE createStream can be called. */
object NikoKeywordConfig {
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
            maxActivePaths = 6,
            keywordsFile = keywords.absolutePath,
            keywordsScore = 1.45f,
            keywordsThreshold = 0.10f,
            numTrailingBlanks = 3,
        )
    }

    private fun prepareKeywords(directory: File): File {
        check(directory.isDirectory || directory.mkdirs()) { "No pude crear la configuración de activación." }
        val destination = File(directory, "niko-keywords.txt")
        if (destination.isFile && destination.readText() == KEYWORDS) return destination
        val temporary = File.createTempFile("niko-keywords-", ".tmp", directory)
        try {
            temporary.writeText(KEYWORDS, Charsets.UTF_8)
            check(temporary.renameTo(destination)) { "No pude guardar las palabras de activación." }
        } finally { temporary.delete() }
        return destination
    }

    // Variantes fonéticas cercanas a "Niko" para tolerar velocidad, acento y vocales.
    // Se conservan tres trailing blanks para evitar activaciones por prefijos de otras palabras.
    private const val KEYWORDS =
        "N IY0 K OW0 :1.45 #0.10 @NIKO\n" +
        "N IY0 K OW1 :1.45 #0.10 @NIKO\n" +
        "N IY1 K OW0 :1.45 #0.10 @NIKO\n" +
        "N IY1 K OW1 :1.45 #0.10 @NIKO\n" +
        "N IH0 K OW0 :1.45 #0.10 @NIKO\n" +
        "N IH1 K OW0 :1.45 #0.10 @NIKO\n" +
        "N IY0 K AH0 :1.50 #0.11 @NIKO\n" +
        "N IH0 K AH0 :1.50 #0.11 @NIKO\n"
}
