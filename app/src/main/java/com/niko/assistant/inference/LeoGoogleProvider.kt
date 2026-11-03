package com.niko.assistant.inference

/** Optional preset; never obtains credentials from Drive or grants consent automatically. */
object LeoGoogleProvider {
    const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    const val MODEL = "gemini-3.1-flash-lite"
    const val FLASH = "gemini-3.8-flash"
    val conversationModels = listOf(MODEL, FLASH)
    fun thinking(model: String, mode: com.niko.assistant.modes.LeoMode): String? = when (model) {
        MODEL -> if (mode == com.niko.assistant.modes.LeoMode.RESEARCH) "medium" else "minimal"
        FLASH -> if (mode == com.niko.assistant.modes.LeoMode.RESEARCH) "medium" else "low"
        else -> null
    }
    const val STUDIO = "https://aistudio.google.com/api-keys"
    const val USAGE = "https://aistudio.google.com/usage"
    const val PRICING = "https://ai.google.dev/gemini-api/docs/pricing"
    fun isGoogle(endpoint: String): Boolean = runCatching {
        java.net.URI(endpoint.trim()).host.equals("generativelanguage.googleapis.com", ignoreCase = true)
    }.getOrDefault(false)
    fun validCredential(endpoint: String, secret: String): Boolean =
        !isGoogle(endpoint) || secret.isNotBlank()
}
