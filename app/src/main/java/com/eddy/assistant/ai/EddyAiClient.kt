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

    val lastError: String? get() = gemini.lastError

    suspend fun healthCheck(): Boolean = gemini.testConnection()

    suspend fun reply(
        message: String,
        memoryContext: String,
        forceWeb: Boolean = false,
        history: List<ConversationTurn> = emptyList(),
    ): EddyAiReply? = gemini.reply(message, memoryContext, useWeb = forceWeb, history = history)
}
