package com.niko.assistant.inference.local

/** Immutable, APK-authorized resources. IDs never accept arbitrary filesystem paths. */
data class LeoLocalModelSpec(val id: String, val bytes: Long, val sha256: String,
    val url: String, val minimumRam: Long, val availableRam: Long, val label: String)

object LeoLocalModelCatalog {
    val compact = LeoLocalModelSpec("qwen3-1.7b-q8", LeoLocalModelStore.BYTES,
        LeoLocalModelStore.SHA256, LeoLocalModelStore.URL_PINNED, 5_000_000_000L, 2_250_000_000L,
        "Qwen3 1.7B · 1,83 GB · RAM física mínima 5 GB")
    val gemma = LeoLocalModelSpec("google-gemma-2-2b", 1_712_438_496L,
        "61b4097f4fa11531649987dc346a07be06ce914c673ba785121b6d9be7234676",
        "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
        4_000_000_000L, 2_000_000_000L,
        "Google Gemma 2 2B (Google DeepMind) · 1,60 GB · RAM mínima 4 GB")
    val large = LeoLocalModelSpec("qwen3-4b-q8", 4_280_404_704L,
        "8c2f07f26af9747e41988551106f149b03eb9b5cb6df636027b6bf6278473300",
        "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/bc640142c66e1fdd12af0bd68f40445458f3869b/Qwen3-4B-Q8_0.gguf",
        8_000_000_000L, 5_000_000_000L, "Qwen3 4B · 4,28 GB · RAM física mínima 8 GB")
    val all = listOf(compact, gemma, large)
    fun find(id: String?): LeoLocalModelSpec? = all.firstOrNull { it.id == id }
}
