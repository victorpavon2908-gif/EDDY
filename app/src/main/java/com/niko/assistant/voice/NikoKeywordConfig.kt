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
            // "Leo" es un hotword muy corto. Conservamos chunk-8 (160 ms) y ampliamos
            // beam/bias para darle más oportunidades al camino del keyword frente a blanks.
            maxActivePaths = 12,
            keywordsFile = keywords.absolutePath,
            keywordsScore = 3.0f,
            keywordsThreshold = 0.04f,
            numTrailingBlanks = 1,
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

    // El modelo es zh/en y el español nicaragüense puede proyectar /le.o/ hacia EH, EY o IY.
    // Todas las variantes devuelven la MISMA etiqueta @LEO. La ruta Canary de respaldo
    // (en NikoLocalVoiceEngine) cubre el caso conocido donde un KWS transducer omite
    // palabras de sólo tres fonemas incluso con score/threshold agresivos.
    private const val KEYWORDS =
        "L EH0 OW0 :3.20 #0.035 @LEO\n" +
        "L EH1 OW0 :3.20 #0.035 @LEO\n" +
        "L EH0 OW1 :3.20 #0.035 @LEO\n" +
        "L EY0 OW0 :3.15 #0.038 @LEO\n" +
        "L EY1 OW0 :3.15 #0.038 @LEO\n" +
        "L EY0 OW1 :3.15 #0.038 @LEO\n" +
        "L IY0 OW0 :3.00 #0.040 @LEO\n" +
        "L IY1 OW0 :3.00 #0.040 @LEO\n" +
        "L IY0 OW1 :3.00 #0.040 @LEO\n"
}
