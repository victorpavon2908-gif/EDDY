package com.eddy.assistant.localai

enum class EddyArchiveType { FILE, TAR_BZ2 }

data class EddyModelSpec(
    val id: String,
    val url: String,
    val archiveType: EddyArchiveType,
    val directoryName: String,
    val expectedFiles: List<String>,
    val minBytes: Long = 64_000L,
    val expectedMinBytes: Map<String, Long> = emptyMap(),
    val requireInstallMarker: Boolean = false,
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
        expectedMinBytes = mapOf("silero_vad.onnx" to 500_000L),
        requireInstallMarker = true,
    )

    val speaker = EddyModelSpec(
        id = "speaker-campplus-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx",
        archiveType = EddyArchiveType.FILE,
        directoryName = "speaker",
        expectedFiles = listOf("3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx"),
        minBytes = 20_000_000L,
        expectedMinBytes = mapOf(
            "3dspeaker_speech_campplus_sv_en_voxceleb_16k.onnx" to 20_000_000L,
        ),
        requireInstallMarker = true,
    )

    // Streaming KWS: chunk 8 is the low-latency variant. It listens locally only for EDDY;
    // Moonshine is not invoked until after the wake word has been detected.
    val keyword = EddyModelSpec(
        id = "kws-eddy-zh-en-2025-v3",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/kws-models/sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20.tar.bz2",
        archiveType = EddyArchiveType.TAR_BZ2,
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

    val spanishAsr = EddyModelSpec(
        id = "asr-moonshine-es-v3",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-base-es-quantized-2026-02-27.tar.bz2",
        archiveType = EddyArchiveType.TAR_BZ2,
        directoryName = "asr",
        expectedFiles = listOf(
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/encoder_model.ort",
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/decoder_model_merged.ort",
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/tokens.txt",
        ),
        minBytes = 10_000_000L,
        expectedMinBytes = mapOf(
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/encoder_model.ort" to 15_000_000L,
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/decoder_model_merged.ort" to 35_000_000L,
            "sherpa-onnx-moonshine-base-es-quantized-2026-02-27/tokens.txt" to 300_000L,
        ),
        requireInstallMarker = true,
    )

    val spanishVoice = EddyModelSpec(
        id = "tts-miro-es-high-v2",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-es_ES-miro-high.tar.bz2",
        archiveType = EddyArchiveType.TAR_BZ2,
        directoryName = "tts",
        expectedFiles = listOf(
            "vits-piper-es_ES-miro-high/es_ES-miro-high.onnx",
            "vits-piper-es_ES-miro-high/tokens.txt",
            "vits-piper-es_ES-miro-high/espeak-ng-data",
        ),
        minBytes = 10_000_000L,
        expectedMinBytes = mapOf(
            "vits-piper-es_ES-miro-high/es_ES-miro-high.onnx" to 10_000_000L,
            "vits-piper-es_ES-miro-high/tokens.txt" to 512L,
        ),
        requireInstallMarker = true,
    )

    val localLlm = EddyModelSpec(
        id = "llm-qwen25-05b-q8-v1",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        archiveType = EddyArchiveType.FILE,
        directoryName = "llm",
        expectedFiles = listOf("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"),
        minBytes = 450_000_000L,
        expectedMinBytes = mapOf(
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task" to 450_000_000L,
        ),
    )

    // Core que debe estar listo para el modo PRO de escucha: KWS local inmediato + VAD + ASR.
    // Voice ID se descarga también, pero no bloquea la activación ni los comandos.
    val voiceCore = listOf(keyword, vad, spanishAsr)
    val acousticCore: List<EddyModelSpec> = voiceCore + speaker

    fun byId(id: String): EddyModelSpec? =
        (voiceCore + speaker + spanishVoice + localLlm).firstOrNull { it.id == id }
}
