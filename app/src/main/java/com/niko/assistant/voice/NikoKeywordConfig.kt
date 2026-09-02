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
            keywordsScore = 1.50f,
            keywordsThreshold = 0.11f,
            numTrailingBlanks = 3,
        )
    }

    private fun prepareKeywords(directory: File): File {
        check(directory.isDirectory || directory.mkdirs()) { "No pude crear la configuración de activación." }
        val destination = File(directory, "leo-keywords.txt")
        if (destination.isFile && destination.readText() == KEYWORDS) return destination
        val temporary = File.createTempFile("leo-keywords-", ".tmp", directory)
        try {
            temporary.writeText(KEYWORDS, Charsets.UTF_8)
            check(temporary.renameTo(destination)) { "No pude guardar las palabras de activación." }
        } finally { temporary.delete() }
        return destination
    }

    // Variantes fonéticas cercanas a "Leo" para tolerar acento y pequeñas diferencias de vocal.
    // El umbral es ligeramente más conservador porque "leo" también existe como palabra común
    // en español. Tres trailing blanks reducen activaciones por prefijos de palabras más largas.
    private const val KEYWORDS =
        "L IY0 OW0 :1.50 #0.11 @LEO\n" +
        "L IY1 OW0 :1.50 #0.11 @LEO\n" +
        "L IY0 OW1 :1.50 #0.11 @LEO\n" +
        "L EH0 OW0 :1.54 #0.12 @LEO\n" +
        "L EH1 OW0 :1.54 #0.12 @LEO\n" +
        "L EH0 OW1 :1.54 #0.12 @LEO\n"
}
