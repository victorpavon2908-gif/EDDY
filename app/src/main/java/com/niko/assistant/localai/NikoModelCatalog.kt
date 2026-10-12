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
    const val SHERPA_VERSION = "1.13.7"

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

    // GTCRN de sherpa-onnx: limpieza local antes de ASR. Es muy pequeño y se instala
    // con el núcleo porque mejora especialmente voz distante/ruido sin usar Internet.
    val denoiser = NikoModelSpec(
        id = "denoiser-gtcrn-simple-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/speech-enhancement-models/gtcrn_simple.onnx",
        archiveType = NikoArchiveType.FILE,
        directoryName = "denoiser",
        expectedFiles = listOf("gtcrn_simple.onnx"),
        minBytes = 500_000L,
        expectedMinBytes = mapOf("gtcrn_simple.onnx" to 500_000L),
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

    // Canary 180M Flash: transcriptor principal rápido y multilingüe.
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
        minBytes = 100_000_000L,
        expectedMinBytes = mapOf(
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/encoder.int8.onnx" to 120_000_000L,
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/decoder.int8.onnx" to 65_000_000L,
            "sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/tokens.txt" to 20_000L,
        ),
        requireInstallMarker = true,
    )

    // Segundo oído local: Whisper multilingual INT8. No se ejecuta en cada frase;
    // refina únicamente transcripciones Canary sospechosas para no duplicar latencia.
    val whisperAsr = NikoModelSpec(
        id = "asr-whisper-tiny-multilingual-int8-v1",
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
        archiveType = NikoArchiveType.TAR_BZ2,
        directoryName = "asr-whisper",
        expectedFiles = listOf(
            "sherpa-onnx-whisper-tiny/tiny-encoder.int8.onnx",
            "sherpa-onnx-whisper-tiny/tiny-decoder.int8.onnx",
            "sherpa-onnx-whisper-tiny/tiny-tokens.txt",
        ),
        minBytes = 60_000_000L,
        expectedMinBytes = mapOf(
            "sherpa-onnx-whisper-tiny/tiny-encoder.int8.onnx" to 10_000_000L,
            "sherpa-onnx-whisper-tiny/tiny-decoder.int8.onnx" to 80_000_000L,
            "sherpa-onnx-whisper-tiny/tiny-tokens.txt" to 500_000L,
        ),
        requireInstallMarker = true,
    )

    // KWS/VAD/GTCRN/Canary forman la escucha mínima que usa el motor de voz en runtime.
    val voiceCore = listOf(keyword, vad, denoiser, spanishAsr)
    val acousticCore: List<NikoModelSpec> = voiceCore + speaker
    val advancedVoice: List<NikoModelSpec> = listOf(whisperAsr)

    /** Voice assets only: the active Kotlin brain does not consume legacy Qwen tasks. */
    fun firstRunModels(@Suppress("UNUSED_PARAMETER") profile: NikoDeviceProfile): List<NikoModelSpec> =
        (acousticCore + advancedVoice).distinctBy { it.id }


}
