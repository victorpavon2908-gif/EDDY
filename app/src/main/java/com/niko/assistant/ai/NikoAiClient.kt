package com.niko.assistant.ai

import android.content.Context
import com.niko.assistant.learning.NikoKnowledgeStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Compatibility facade. Only a public query goes to search; never memory, history or screen data. */
class NikoAiClient(
    context: Context,
    @Suppress("UNUSED_PARAMETER") baseUrlOverride: String? = null,
    private val search: suspend (String) -> NikoAiReply = { LeoNativeWebSearch.search(it, mode = com.niko.assistant.modes.LeoModeStore.read(context.applicationContext)) },
) {
    private val appContext = context.applicationContext
    private val knowledge = NikoKnowledgeStore(appContext)
    init { NikoAiSettings.retireCloudCredentials(context.applicationContext) }
    val isConfigured: Boolean get() = true
    @Volatile var lastError: String? = null
        private set

    suspend fun describeObservation(@Suppress("UNUSED_PARAMETER") message: String, observation: com.niko.assistant.devicecontrol.LeoVisionContext): String =
        if (observation.usableAt(System.currentTimeMillis())) observation.offlineDescription()
        else "La observación ya no está disponible. Pedime mirar la pantalla de nuevo."

    suspend fun reply(
        message: String,
        @Suppress("UNUSED_PARAMETER") memoryContext: String,
        forceWeb: Boolean = false,
        @Suppress("UNUSED_PARAMETER") history: List<ConversationTurn> = emptyList(),
        @Suppress("UNUSED_PARAMETER") onDelta: (suspend (String) -> Unit)? = null,
    ): NikoAiReply? {
        if (AutonomousResearch.offlineOnly(message) || !forceWeb) return null
        // Search extracts the subject itself; retain accents, recency intent and case-sensitive URLs.
        val result = search(message)
        currentCoroutineContext().ensureActive()
        lastError = result.text.takeUnless { result.webUsed }
        if (knowledge.isRejected(result.text)) return NikoAiReply(
            "La búsqueda repitió una respuesta que marcaste como incorrecta. No la voy a reutilizar; necesitás otras fuentes o una corrección concreta.", false, emptyList())
        return result
    }
}
