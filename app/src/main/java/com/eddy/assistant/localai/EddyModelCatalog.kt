package com.eddy.assistant.localai

enum class EddyArchiveType { FILE, TAR_BZ2 }

data class EddyModelSpec(
    val id: String,
    val url: String,
    val archiveType: EddyArchiveType,
    val directoryName: String,
    val expectedFiles: List<String>,
    val minBytes: Long = 64_000L,
)

object EddyModelCatalog {
    const val SHERPA_VERSION = "1.13.6"

    val vad = EddyModelSpec(
        id = "vad-silero-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        archiveType = EddyArchiveType.FILE,
        directoryName = "vad",
        expectedFiles = listOf("silero_vad.onnx"),
        minBytes = 500_000L,
    )

    val speaker = EddyModelSpec(
        id = "speaker-campplus-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
        archiveType = EddyArchiveType.FILE,
        directoryName = "speaker",
        expectedFiles = listOf("3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"),
        minBytes = 20_000_000L,
    )

    val keyword = EddyModelSpec(
        id = "kws-eddy-en-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01.tar.bz2",
        archiveType = EddyArchiveType.TAR_BZ2,
        directoryName = "kws",
        expectedFiles = listOf(
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/tokens.txt",
            "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01/bpe.model",
        ),
        minBytes = 5_000_000L,
    )

    val spanishAsr = EddyModelSpec(
        id = "asr-moonshine-es-v2",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-es-quantized-2026-02-27.tar.bz2",
        archiveType = EddyArchiveType.TAR_BZ2,
        directoryName = "asr",
        expectedFiles = listOf(
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/encoder_model.ort",
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/decoder_model_merged.ort",
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/tokens.txt",
        ),
        minBytes = 10_000_000L,
    )

    val spanishVoice = EddyModelSpec(
        id = "tts-miro-es-high-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-miro-high.tar.bz2",
        archiveType = EddyArchiveType.TAR_BZ2,
        directoryName = "tts",
        expectedFiles = listOf(
            "vits-piper-es_ES-miro-high/es_ES-miro-high.onnx",
            "vits-piper-es_ES-miro-high/tokens.txt",
            "vits-piper-es_ES-miro-high/espeak-ng-data",
        ),
        minBytes = 10_000_000L,
    )

    // Modelo conversacional local y multilingüe. No se descarga en teléfonos LITE.
    val localLlm = EddyModelSpec(
        id = "llm-qwen25-05b-q8-v1",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        archiveType = EddyArchiveType.FILE,
        directoryName = "llm",
        expectedFiles = listOf("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"),
        minBytes = 450_000_000L,
    )

    val acousticCore = listOf(vad, speaker, keyword, spanishAsr, spanishVoice)
}
