package com.eddy.assistant.ai

import android.content.Context

/**
 * Compatibility facade used by the assistant service.
 * Conversation goes EDDY -> GroqCloud directly. There is no EDDY/Render backend hop.
 * Local commands are still handled by LocalBrain/ActionExecutor before this client is called.
 */
class EddyAiClient(
    context: Context,
    @Suppress("UNUSED_PARAMETER") baseUrlOverride: String? = null,
) {
    private val appContext = context.applicationContext
    private val groq = EddyGroqClient(appContext)

    val isConfigured: Boolean get() = groq.isConfigured

    val lastError: String? get() = groq.lastError

    suspend fun healthCheck(): Boolean = groq.testConnection()

    suspend fun reply(
        message: String,
        memoryContext: String,
        forceWeb: Boolean = false,
        history: List<ConversationTurn> = emptyList(),
    ): EddyAiReply? {
        if (AutonomousResearch.offlineOnly(message)) return null
        val allowWeb = forceWeb || (EddyAiSettings.autoResearch(appContext) && AutonomousResearch.allowedFor(message))
        return groq.reply(message, memoryContext, useWeb = allowWeb, history = history)
    }
}
