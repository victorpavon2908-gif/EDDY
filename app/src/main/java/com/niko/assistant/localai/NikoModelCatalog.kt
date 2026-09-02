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

    // Segundo oído opcional: Whisper multilingual INT8. No se ejecuta en cada frase;
    // refina únicamente transcripciones Canary sospechosas para no duplicar la latencia.
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

    // Voz masculina latinoamericana local. Claude high ofrece mejor naturalidad que
    // las voces Piper medium y mantiene una latencia razonable en Android.
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

    // Cerebro rápido: útil en teléfonos con menos RAM o como respaldo de recuperación.
    val localLlmFast = NikoModelSpec(
        id = "llm-qwen25-05b-q8-v1",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        archiveType = NikoArchiveType.FILE,
        directoryName = "llm-fast",
        expectedFiles = listOf("Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"),
        minBytes = 450_000_000L,
        expectedMinBytes = mapOf(
            "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task" to 450_000_000L,
        ),
    )

    // Cerebro de calidad para Android: Qwen2.5 1.5B Instruct INT8, listo para
    // MediaPipe/LiteRT. Usa KV 1280 para priorizar velocidad y memoria sobre contexto
    // excesivo; NIKO conserva contexto largo mediante su memoria local resumida.
    val localLlmQuality = NikoModelSpec(
        id = "llm-qwen25-15b-q8-v1",
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        archiveType = NikoArchiveType.FILE,
        directoryName = "llm-quality",
        expectedFiles = listOf("Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"),
        minBytes = 1_450_000_000L,
        expectedMinBytes = mapOf(
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task" to 1_450_000_000L,
        ),
    )

    // Alias conservado para ajustes/tests antiguos. La selección real depende del dispositivo.
    val localLlm: NikoModelSpec get() = localLlmFast
    val conversationModels: List<NikoModelSpec> get() = listOf(localLlmQuality, localLlmFast)

    fun recommendedConversationModel(profile: NikoDeviceProfile): NikoModelSpec =
        if (profile.prefersQualityLocalLlm) localLlmQuality else localLlmFast

    // KWS/VAD/Canary bloquean el modo PRO. Whisper, voz, Voice ID y LLM son mejoras opcionales.
    val voiceCore = listOf(keyword, vad, spanishAsr)
    val acousticCore: List<NikoModelSpec> = voiceCore + speaker + spanishVoice
    val advancedVoice: List<NikoModelSpec> = listOf(whisperAsr)

    fun byId(id: String): NikoModelSpec? =
        (voiceCore + speaker + spanishVoice + advancedVoice + conversationModels).firstOrNull { it.id == id }
}
