package com.niko.assistant.localai

import android.content.Context
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.learning.NikoKnowledgeStore
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Cerebro generativo completamente local. El modelo se descarga una vez y nunca
 * recibe datos desde Internet durante la inferencia.
 *
 * Antes de gastar inferencia generativa, consulta la memoria documental local. Eso
 * permite que investigaciones web anteriores sigan siendo útiles sin conexión.
 */
class NikoLocalLlm(
    private val context: Context,
    private val models: NikoModelManager,
) {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val knowledge = NikoKnowledgeStore(context.applicationContext)
    @Volatile private var closed = false
    @Volatile private var inference: LlmInference? = null
    @Volatile var lastError: String? = null
        private set

    val isAvailable: Boolean
        get() = models.isInstalled(NikoModelCatalog.localLlm)

    suspend fun reply(
        message: String,
        memoryContext: String = "",
        evidence: String = "",
    ): String? = withContext(Dispatchers.Default) {
        lastError = null
        if (closed) return@withContext null

        // Sourced knowledge can answer by itself even when the local LLM model has not
        // been downloaded yet. Current/time-sensitive questions are rejected by the store.
        runCatching { knowledge.recall(message) }.getOrNull()?.let { learned ->
            return@withContext learned.answer
        }

        if (!isAvailable) {
            lastError = "Prepará el modelo de conversación sin Internet en Ajustes."
            return@withContext null
        }
        mutex.withLock {
            if (closed) return@withLock null
            val engine = inference ?: createEngine() ?: return@withLock null
            runCatching {
                val prompt = LocalConversationPrompt.fit(message, memoryContext, evidence, NikoAiSettings.personality(context), engine::sizeInTokens)
                    ?: run {
                        lastError = "La consulta supera la capacidad del modelo local. Probá una pregunta más corta."
                        return@withLock null
                    }
                engine.generateResponse(prompt).trim()
            }
                .onFailure { lastError = "El modelo local no pudo responder. Probá una pregunta más corta o reiniciá el asistente." }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                .also { if (it == null && lastError == null) lastError = "El modelo local no devolvió una respuesta." }
        }
    }

    fun release() {
        closed = true
        cleanupScope.launch {
            mutex.withLock { runCatching { inference?.close() }; inference = null }
            cleanupScope.cancel()
        }
    }

    private fun createEngine(): LlmInference? {
        val path = models.file(NikoModelCatalog.localLlm).absolutePath
        return runCatching {
            // En MediaPipe 0.10.24 topK/topP/temperature pertenecen a la sesión,
            // no a LlmInferenceOptions. El motor base usa sus valores seguros por defecto.
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                // This is the combined prompt + reply budget, not just the output length.
                .setMaxTokens(LocalConversationPrompt.MODEL_TOKENS)
                .build()
            LlmInference.createFromOptions(context.applicationContext, options).also { inference = it }
        }.onFailure {
            lastError = "No pude iniciar el modelo local. Comprobá que haya memoria disponible y reiniciá el asistente."
        }.getOrNull()
    }

}
