package com.niko.assistant.localai

enum class NikoArchiveType { FILE, TAR_BZ2 }

data class NikoModelSpec(
    val id: String,
    val url: String,
    val archiveType: NikoArchiveType,
    val directoryName: String,
    val expectedFiles: List<String>,
    val minBytes: Long = 64_000L,
    val expectedMinBytes: Map<String, Long> = emptyMap(),
    val requireInstallMarker: Boolean = false,
)

object NikoModelCatalog {
    const val SHERPA_VERSION = "1.13.6"

    val vad = NikoModelSpec(
        id = "vad-silero-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx",
        archiveType = NikoArchiveType.FILE,
        directoryName = "vad",
        expectedFiles = listOf("silero_vad.onnx"),
        minBytes = 500_000L,
        expectedMinBytes = mapOf("silero_vad.onnx" to 500_000L),
        requireInstallMarker = true,
    )

    val speaker = NikoModelSpec(
        id = "speaker-campplus-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
        archiveType = NikoArchiveType.FILE,
        directoryName = "speaker",
        expectedFiles = listOf("3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"),
        minBytes = 20_000_000L,
        expectedMinBytes = mapOf(
            "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx" to 20_000_000L,
        ),
        requireInstallMarker = true,
    )

    val keyword = NikoModelSpec(
        id = "kws-niko-zh-en-2025-v3",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2",
        archiveType = NikoArchiveType.TAR_BZ2,
        directoryName = "kws",
        expectedFiles = listOf(
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/encoder-epoch-13-avg-2-chunk-8-left-64.int8.onnx",
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/decoder-epoch-13-avg-2-chunk-8-left-64.onnx",
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/joiner-epoch-13-avg-2-chunk-8-left-64.int8.onnx",
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/tokens.txt",
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/en.phone",
        ),
        minBytes = 30_000_000L,
        expectedMinBytes = mapOf(
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/encoder-epoch-13-avg-2-chunk-8-left-64.int8.onnx" to 4_000_000L,
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/decoder-epoch-13-avg-2-chunk-8-left-64.onnx" to 600_000L,
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/joiner-epoch-13-avg-2-chunk-8-left-64.int8.onnx" to 70_000L,
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/tokens.txt" to 1_000L,
            "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20/en.phone" to 1_000_000L,
        ),
        requireInstallMarker = true,
    )

    // Canary 180M Flash: modelo NeMo multilingüe de mayor precisión que Moonshine base.
    // El paquete INT8 está preparado por sherpa-onnx para inferencia completamente local.
    val spanishAsr = NikoModelSpec(
        id = "asr-canary-180m-es-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8.tar.bz2",
        archiveType = NikoArchiveType.TAR_BZ2,
        directoryName = "asr",
        expectedFiles = listOf(
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/encoder.int8.onnx",
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/decoder.int8.onnx",
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/tokens.txt",
        ),
        // This threshold applies to the compressed download. Per-file bounds below
        // validate the actual installed model after extraction.
        minBytes = 100_000_000L,
        expectedMinBytes = mapOf(
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/encoder.int8.onnx" to 120_000_000L,
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/decoder.int8.onnx" to 65_000_000L,
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/tokens.txt" to 20_000L,
        ),
        requireInstallMarker = true,
    )

    // Voz masculina latinoamericana. La base mexicana se acerca mucho más al color de voz
    // centroamericano que la antigua voz es-ES y sigue funcionando 100% local.
    val spanishVoice = NikoModelSpec(
        id = "tts-claude-es-mx-high-v3",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_MX-claude-high.tar.bz2",
        archiveType = NikoArchiveType.TAR_BZ2,
        directoryName = "tts",
        expectedFiles = listOf(
            "vits-piper-es_MX-claude-high/es_MX-claude-high.onnx",
            "vits-piper-es_MX-claude-high/tokens.txt",
            "vits-piper-es_MX-claude-high/espeak-ng-data",
        ),
        minBytes = 10_000_000L,
        expectedMinBytes = mapOf(
            "vits-piper-es_MX-claude-high/es_MX-claude-high.onnx" to 10_000_000L,
            "vits-piper-es_MX-claude-high/tokens.txt" to 512L,
        ),
        requireInstallMarker = true,
    )

    val localLlm = NikoModelSpec(
        id = "llm-qwen25-05b-q8-v1",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        archiveType = NikoArchiveType.FILE,
        directoryName = "llm",
        expectedFiles = listOf("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"),
        minBytes = 450_000_000L,
        expectedMinBytes = mapOf(
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task" to 450_000_000L,
        ),
    )

    // KWS/VAD/ASR bloquean el modo PRO. La voz y Voice ID son mejoras opcionales:
    // se descargan solas, pero si fallan NIKO sigue funcionando con la voz del sistema.
    val voiceCore = listOf(keyword, vad, spanishAsr)
    val acousticCore: List<NikoModelSpec> = voiceCore + speaker + spanishVoice

    fun byId(id: String): NikoModelSpec? =
        (voiceCore + speaker + spanishVoice + localLlm).firstOrNull { it.id == id }
}
