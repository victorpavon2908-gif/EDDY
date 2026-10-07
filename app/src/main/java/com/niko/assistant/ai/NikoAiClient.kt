package com.niko.assistant.ai

import android.content.Context
import kotlinx.coroutines.ensureActive
import com.niko.assistant.brain.WebQueryRouter
import com.niko.assistant.learning.NikoKnowledgeStore

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

    private fun conciseWebAnswer(reply: NikoAiReply, @Suppress("UNUSED_PARAMETER") subject: String): NikoAiReply {
        if (!reply.webUsed || reply.text.isBlank()) return reply
        var text = reply.text.trim()
        text = text
            .replace(Regex("(?im)^\\s*(?:respuesta breve(?: sobre [^:]+)?|detalles(?: y contexto| y verificacion)?|contraste y limites)\\s*:\\s*"), "")
            .replace(Regex("(?i)^(?:busqu[eé]|investigu[eé]|consult[eé])[^.!?]*[.!?]\\s*"), "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

        return reply.copy(text = text.ifBlank { reply.text.trim() })
    }

    /** Keeps the settings-screen Groq test meaningful; native search itself needs no setup. */
    suspend fun healthCheck(): Boolean = groq.testConnection()

    /** Screen data never goes to web search. No observation means no vision claim. */
    suspend fun describeObservation(message: String, observation: com.niko.assistant.devicecontrol.LeoVisionContext): String {
        if (!observation.usableAt(System.currentTimeMillis())) return "La observación ya no está disponible. Pedime mirar la pantalla de nuevo."
        if (!groq.isConfigured || AutonomousResearch.offlineOnly(message)) return observation.offlineDescription()
        return groq.reply(message, observation.asUntrustedData(), useWeb = false)?.text
            ?: observation.offlineDescription()
    }

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
            // Native retrieval is authoritative. Compound remains a fallback rather than a
            // duplicate concurrent investigation that delays an already usable native answer.
            val native = LeoNativeWebSearch.search(subject)
            val compound = if (!native.webUsed && groq.isConfigured) {
                groq.reply(subject, "", useWeb = true, history = emptyList())
            } else null
            val validatedCompound = compound?.takeIf(ResearchCitationPolicy::accepts)

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
            val finalReply = conciseWebAnswer(researched, subject)
            nativeLastError = if (finalReply.webUsed) null else finalReply.text
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (finalReply.webUsed && finalReply.sources.isNotEmpty()) {
                runCatching { knowledge.learn(subject, finalReply) }
            }
            return finalReply
        }

        // Conversation remains optional cloud assistance. If there is no Groq key,
        // return null so the coordinator can stay local/fallback without blocking search.
        if (!groq.isConfigured) return null
        nativeLastError = null
        val allowGroqWeb = onDelta == null && NikoAiSettings.autoResearch(appContext) &&
            AutonomousResearch.allowedFor(message) &&
            !WebQueryRouter.needsCurrentInformation(message)
        val reply = groq.reply(message, memoryContext, useWeb = allowGroqWeb, history = history, onDelta = onDelta)
        val subject = WebQueryRouter.explicitQuery(message) ?: message
        val finalReply = if (reply != null && reply.webUsed) conciseWebAnswer(reply, subject) else reply
        if (finalReply != null && finalReply.webUsed && finalReply.sources.isNotEmpty()) {
            runCatching { knowledge.learn(subject, finalReply) }
        }
        return finalReply
    }
}
