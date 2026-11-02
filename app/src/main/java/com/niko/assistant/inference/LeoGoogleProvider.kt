package com.niko.assistant.inference

/** Optional preset; never obtains credentials from Drive or grants consent automatically. */
object LeoGoogleProvider {
    const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    const val MODEL = "gemini-3.8-flash"
    const val STUDIO = "https://aistudio.google.com/api-keys"
    const val USAGE = "https://aistudio.google.com/usage"
    const val PRICING = "https://ai.google.dev/gemini-api/docs/pricing"
    fun isGoogle(endpoint: String): Boolean = runCatching {
        java.net.URI(endpoint.trim()).host.equals("generativelanguage.googleapis.com", ignoreCase = true)
    }.getOrDefault(false)
    fun validCredential(endpoint: String, secret: String): Boolean =
        !isGoogle(endpoint) || secret.isNotBlank()
}
