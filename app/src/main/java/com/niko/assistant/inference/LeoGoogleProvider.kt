package com.niko.assistant.inference

/** Optional preset; never obtains credentials from Drive or grants consent automatically. */
object LeoGoogleProvider {
    const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions"
    const val MODEL = "gemini-3.8-flash"
    fun validCredential(endpoint: String, secret: String): Boolean =
        !java.net.URI(endpoint.trim()).host.equals("generativelanguage.googleapis.com", ignoreCase = true) || secret.isNotBlank()
}
