package com.niko.assistant.localai

import android.content.Context
import com.niko.assistant.devicecontrol.NikoVisualContext
import com.niko.assistant.learning.NikoKnowledgeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fachada segura del cerebro local.
 *
 * LEO 0.10.1 prioriza que el núcleo funcione en cualquier Android 12+ sin importar
 * fabricante. El runtime MediaPipe/LlmInference fue retirado del proceso principal
 * porque un fallo JNI puede provocar SIGSEGV y cerrar toda la aplicación, algo que
 * Kotlin no puede recuperar con try/catch.
 *
 * Esta clase conserva la API usada por el resto del asistente: memoria aprendida y
 * rutas deterministas siguen funcionando, pero generación/planificación LLM devuelve
 * null para que el coordinador continúe con búsqueda nativa, reglas locales o nube
 * opcional. Ninguna orden básica depende de un LLM nativo.
 */
class NikoLocalLlm(
    context: Context,
    @Suppress("UNUSED_PARAMETER") models: NikoModelManager,
) {
    private val appContext = context.applicationContext
    private val knowledge = NikoKnowledgeStore(appContext)

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
     * Los routers de acciones vuelven al parser determinista cuando no hay LLM.
     * Esto es intencional: una clasificación avanzada nunca debe arriesgar el proceso.
     */
    suspend fun completeStructured(@Suppress("UNUSED_PARAMETER") instruction: String): String? {
        if (!closed) lastError = universalSafeModeMessage()
        return null
    }

    fun release() {
        closed = true
    }

    private fun universalSafeModeMessage(): String =
        "LEO está usando el núcleo universal seguro de Android 12+: voz, acciones, memoria y búsqueda web siguen disponibles sin cargar un LLM JNI en el proceso principal."
}
