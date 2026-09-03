package com.niko.assistant.localai

import android.content.Context
import com.niko.assistant.ai.LeoStructuredGroq
import com.niko.assistant.devicecontrol.NikoVisualContext
import com.niko.assistant.learning.NikoKnowledgeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fachada segura del cerebro local.
 *
 * LEO conserva deshabilitado el runtime generativo JNI dentro del proceso principal
 * para evitar cierres nativos en Android 12+. Memoria aprendida y rutas deterministas
 * siguen funcionando localmente. La compilación semántica de acciones, cuando hace falta,
 * se delega a un adaptador Groq aislado que devuelve solo texto DSL para validación local.
 */
class NikoLocalLlm(
    context: Context,
    @Suppress("UNUSED_PARAMETER") models: NikoModelManager,
) {
    private val appContext = context.applicationContext
    private val knowledge = NikoKnowledgeStore(appContext)
    private val structuredCloud = LeoStructuredGroq(appContext)

    @Volatile private var closed = false

    @Volatile var lastError: String? = null
        private set

    /** Nunca cargamos un runtime JNI generativo dentro del proceso principal. */
    val runtimeSupported: Boolean get() = false

    /** Evita prewarm y cualquier intento accidental de inicialización nativa. */
    val isAvailable: Boolean get() = false

    val preferredModel: NikoModelSpec get() = NikoModelCatalog.localLlmFast

    suspend fun prewarm(): Boolean {
        lastError = universalSafeModeMessage()
        return false
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

        // El conocimiento aprendido local no necesita un LLM y sigue disponible.
        if (!NikoVisualContext.wantsScreenContext(message)) {
            runCatching { knowledge.recall(message) }.getOrNull()?.let { learned ->
                return@withContext learned.answer
            }
        }

        lastError = universalSafeModeMessage()
        null
    }

    /**
     * Compatibilidad con los routers existentes: no usa el LLM local desactivado.
     * Groq solo compila intención -> DSL; el plan resultante todavía debe pasar por
     * LeoStructuredPlanner antes de que cualquier acción pueda ejecutarse.
     */
    suspend fun completeStructured(instruction: String): String? {
        if (closed) return null
        val result = structuredCloud.complete(instruction)
        lastError = if (result == null) structuredCloud.lastError ?: universalSafeModeMessage() else null
        return result
    }

    fun release() {
        closed = true
    }

    private fun universalSafeModeMessage(): String =
        "LEO está usando el núcleo universal seguro de Android 12+: voz, acciones, memoria y búsqueda web siguen disponibles sin cargar un LLM JNI en el proceso principal."
}
