package com.niko.assistant.ai

import android.content.Context
import com.niko.assistant.learning.NikoKnowledgeStore

/**
 * Compatibility facade used by the assistant service.
 * Conversation goes NIKO -> GroqCloud directly. There is no NIKO/Render backend hop.
 * Local commands are still handled by LocalBrain/ActionExecutor before this client is called.
 *
 * When Groq returns sourced web research, NIKO stores a bounded local copy so future
 * evergreen questions can be answered without repeating the network request.
 */
class NikoAiClient(
    context: Context,
    @Suppress("UNUSED_PARAMETER") baseUrlOverride: String? = null,
) {
    private val appContext = context.applicationContext
    private val groq = NikoGroqClient(appContext)
    private val knowledge = NikoKnowledgeStore(appContext)

    val isConfigured: Boolean get() = groq.isConfigured

    val lastError: String? get() = groq.lastError

    suspend fun healthCheck(): Boolean = groq.testConnection()

    suspend fun reply(
        message: String,
        memoryContext: String,
        forceWeb: Boolean = false,
        history: List<ConversationTurn> = emptyList(),
    ): NikoAiReply? {
        if (AutonomousResearch.offlineOnly(message)) return null
        val allowWeb = forceWeb || (NikoAiSettings.autoResearch(appContext) && AutonomousResearch.allowedFor(message))
        val reply = groq.reply(message, memoryContext, useWeb = allowWeb, history = history)
        if (reply != null && reply.webUsed && reply.sources.isNotEmpty()) {
            runCatching { knowledge.learn(message, reply) }
        }
        return reply
    }
}
