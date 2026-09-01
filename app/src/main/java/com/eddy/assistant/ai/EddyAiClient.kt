package com.eddy.assistant.ai

import android.content.Context

data class EddyWebSource(val title: String, val url: String)

data class EddyAiReply(
    val text: String,
    val webUsed: Boolean,
    val sources: List<EddyWebSource>,
    val evidence: String = "",
)

/**
 * Compatibility facade used by the assistant service.
 * Conversation now goes EDDY -> Gemini directly. There is no EDDY/Render backend hop.
 * Local commands are still handled by LocalBrain/ActionExecutor before this client is called.
 */
class EddyAiClient(
    context: Context,
    @Suppress("UNUSED_PARAMETER") baseUrlOverride: String? = null,
) {
    private val appContext = context.applicationContext
    private val gemini = EddyGeminiClient(appContext)

    val isConfigured: Boolean get() = gemini.isConfigured

    suspend fun healthCheck(): Boolean = gemini.testConnection()

    suspend fun reply(
        message: String,
        memoryContext: String,
        forceWeb: Boolean = false,
    ): EddyAiReply? {
        val prompt = if (forceWeb) {
            "El usuario necesita información actual o externa. Respondé con claridad; si no podés verificar actualidad, decilo explícitamente. Pregunta: $message"
        } else message
        val text = gemini.reply(prompt, memoryContext) ?: return null
        return EddyAiReply(text = text, webUsed = false, sources = emptyList())
    }
}
