package com.niko.assistant.ai

import android.content.Context
import com.niko.assistant.brain.WebQueryRouter
import com.niko.assistant.learning.NikoKnowledgeStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Compatibility facade used by the assistant service.
 *
 * Web research no longer requires GroqCloud: forced/current searches go through
 * [LeoNativeWebSearch], which performs keyless HTTP discovery + local extraction and
 * summarization on the phone. Groq optionally synthesizes that evidence and supports conversation.
 */
class NikoAiClient(
    context: Context,
    @Suppress("UNUSED_PARAMETER") baseUrlOverride: String? = null,
) {
    private val appContext = context.applicationContext
    private val groq = NikoGroqClient(appContext)
    private val knowledge = NikoKnowledgeStore(appContext)

    @Volatile private var nativeLastError: String? = null

    /**
     * The service historically used this flag to decide whether Internet research was
     * available. Native search needs no API key, so research is always configured.
     * Individual HTTP attempts can still fail when the phone has no Internet.
     */
    val isConfigured: Boolean get() = true

    val lastError: String? get() = nativeLastError ?: groq.lastError

    /** Keeps the settings-screen Groq test meaningful; native search itself needs no setup. */
    suspend fun healthCheck(): Boolean = groq.testConnection()

    suspend fun reply(
        message: String,
        memoryContext: String,
        forceWeb: Boolean = false,
        history: List<ConversationTurn> = emptyList(),
        onDelta: (suspend (String) -> Unit)? = null,
    ): NikoAiReply? {
        if (AutonomousResearch.offlineOnly(message)) return null

        if (forceWeb) {
            val subject = WebQueryRouter.explicitQuery(message) ?: message
            val (native, compound) = if (groq.isConfigured) {
                coroutineScope {
                    val nativeTask = async { LeoNativeWebSearch.search(subject) }
                    // Search receives only the requested subject: personal memory and
                    // dialogue history must never leak into provider search queries.
                    val compoundTask = async { groq.reply(subject, "", useWeb = true, history = emptyList()) }
                    nativeTask.await() to compoundTask.await()
                }
            } else {
                LeoNativeWebSearch.search(subject) to null
            }
            val validatedCompound = compound?.takeIf { it.webUsed && it.sources.isNotEmpty() }

            // La búsqueda nativa recupera y valida las fuentes; después, cuando Groq está
            // disponible, siempre intentamos convertir esos hallazgos en una respuesta
            // razonada. Antes solo sintetizábamos cuando Compound fallaba, por eso muchas
            // respuestas terminaban sonando como una lectura de extractos del buscador.
            val interpretedNative = if (native.webUsed && native.sources.isNotEmpty() && groq.isConfigured) {
                groq.synthesizeResearch(subject, native) ?: native
            } else native

            val researched = when {
                interpretedNative.webUsed && interpretedNative.sources.isNotEmpty() && interpretedNative !== native -> interpretedNative
                validatedCompound != null -> validatedCompound
                else -> native
            }
            nativeLastError = if (researched.webUsed) null else researched.text
            if (researched.webUsed && researched.sources.isNotEmpty()) {
                runCatching { knowledge.learn(subject, researched) }
            }
            return researched
        }

        // Conversation remains optional cloud assistance. If there is no Groq key,
        // return null so the coordinator can stay local/fallback without blocking search.
        if (!groq.isConfigured) return null
        nativeLastError = null
        val allowGroqWeb = onDelta == null && NikoAiSettings.autoResearch(appContext) &&
            AutonomousResearch.allowedFor(message) &&
            !WebQueryRouter.needsCurrentInformation(message)
        val reply = groq.reply(message, memoryContext, useWeb = allowGroqWeb, history = history, onDelta = onDelta)
        if (reply != null && reply.webUsed && reply.sources.isNotEmpty()) {
            val subject = WebQueryRouter.explicitQuery(message) ?: message
            runCatching { knowledge.learn(subject, reply) }
        }
        return reply
    }
}
