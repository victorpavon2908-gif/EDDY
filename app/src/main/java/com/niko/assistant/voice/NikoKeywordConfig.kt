package com.niko.assistant.voice

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File
import java.util.Locale

/** File-based JNI construction validates keywords BEFORE createStream can be called. */
object NikoKeywordConfig {
    fun create(modelDirectory: File, configDirectory: File): KeywordSpotterConfig {
        val tuning = LeoVoiceTuning.current()
        val keywords = prepareKeywords(configDirectory, tuning)
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
            keywordsScore = tuning.keywordScore,
            keywordsThreshold = tuning.keywordThreshold,
            numTrailingBlanks = tuning.trailingBlanks,
        )
    }

    private fun prepareKeywords(directory: File, tuning: LeoVoiceTuningProfile): File {
        check(directory.isDirectory || directory.mkdirs()) { "No pude crear la configuración de activación." }
        val destination = File(directory, "leo-keywords.txt")
        val expected = keywordText(tuning)
        if (destination.isFile && destination.readText() == expected) return destination
        val temporary = File.createTempFile("leo-keywords-", ".tmp", directory)
        try {
            temporary.writeText(expected, Charsets.UTF_8)
            check(temporary.renameTo(destination)) { "No pude guardar las palabras de activación." }
        } finally { temporary.delete() }
        return destination
    }

    internal fun keywordText(tuning: LeoVoiceTuningProfile = LeoVoiceTuning.current()): String {
        val primaryScore = tuning.keywordScore
        val primaryThreshold = tuning.keywordThreshold
        val secondaryScore = (primaryScore + 0.04f).coerceAtMost(1.84f)
        val secondaryThreshold = (primaryThreshold + 0.01f).coerceAtMost(0.19f)
        fun score(value: Float) = String.format(Locale.US, "%.2f", value)
        return "L IY0 OW0 :${score(primaryScore)} #${score(primaryThreshold)} @LEO\n" +
            "L IY1 OW0 :${score(primaryScore)} #${score(primaryThreshold)} @LEO\n" +
            "L IY0 OW1 :${score(primaryScore)} #${score(primaryThreshold)} @LEO\n" +
            "L EH0 OW0 :${score(secondaryScore)} #${score(secondaryThreshold)} @LEO\n" +
            "L EH1 OW0 :${score(secondaryScore)} #${score(secondaryThreshold)} @LEO\n" +
            "L EH0 OW1 :${score(secondaryScore)} #${score(secondaryThreshold)} @LEO\n"
    }
}
