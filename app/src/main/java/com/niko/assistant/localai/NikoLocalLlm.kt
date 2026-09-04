package com.niko.assistant.localai

import android.content.Context
import com.niko.assistant.ai.LeoStructuredGroq
import com.niko.assistant.devicecontrol.NikoVisualContext
import com.niko.assistant.learning.NikoKnowledgeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stable local-first brain facade.
 *
 * Open-domain native JNI LLMs stay disabled because they previously crashed the Android 12+
 * process. Short conversational generation is now handled by Leo MicroGPT, a tiny pure-Kotlin
 * Transformer bundled with the APK. Learned knowledge stays local and Groq remains the validated
 * structured/action and open-domain fallback.
 */
class NikoLocalLlm(
    context: Context,
    @Suppress("UNUSED_PARAMETER") models: NikoModelManager,
) {
    private val appContext = context.applicationContext
    private val knowledge = NikoKnowledgeStore(appContext)
    private val structuredCloud = LeoStructuredGroq(appContext)
    private val microGpt = LeoMicroGptAsset(appContext)

    @Volatile private var closed = false

    @Volatile var lastError: String? = null
        private set

    /** Pure Kotlin conversational generation is supported without loading JNI. */
    val runtimeSupported: Boolean get() = microGpt.isAvailable

    /** The bundled checkpoint can be prewarmed without downloading a model. */
    val isAvailable: Boolean get() = !closed && microGpt.isAvailable

    val preferredModel: NikoModelSpec get() = NikoModelCatalog.localLlmFast

    suspend fun prewarm(): Boolean = withContext(Dispatchers.IO) {
        if (closed) return@withContext false
        val ready = microGpt.prewarm()
        lastError = if (ready) null else localModelError()
        ready
    }

    suspend fun reply(
        message: String,
        memoryContext: String = "",
        evidence: String = "",
    ): String? = withContext(Dispatchers.IO) {
        @Suppress("UNUSED_VARIABLE") val ignoredContext = memoryContext
        @Suppress("UNUSED_VARIABLE") val ignoredEvidence = evidence
        lastError = null
        if (closed) return@withContext null

        if (!NikoVisualContext.wantsScreenContext(message)) {
            runCatching { knowledge.recall(message) }.getOrNull()?.let { learned ->
                return@withContext learned.answer
            }
            microGpt.reply(message)?.let { generated ->
                return@withContext generated
            }
        }

        lastError = localModelError()
        null
    }

    /**
     * Groq only compiles intention -> DSL; LeoStructuredPlanner still validates every resulting
     * action. MicroGPT never emits executable DSL or receives permission to perform phone actions.
     */
    suspend fun completeStructured(instruction: String): String? {
        if (closed) return null
        val result = structuredCloud.complete(instruction)
        lastError = if (result == null) structuredCloud.lastError ?: localModelError() else null
        return result
    }

    fun release() {
        closed = true
    }

    private fun localModelError(): String =
        "Mi MicroGPT local cubre conversación breve y segura. Para conocimiento amplio o datos actuales uso Groq o búsqueda web cuando están disponibles."
}
