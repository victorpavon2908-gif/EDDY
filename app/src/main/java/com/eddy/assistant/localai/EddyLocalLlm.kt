package com.eddy.assistant.localai

import android.content.Context
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
 */
class EddyLocalLlm(
    private val context: Context,
    private val models: EddyModelManager,
) {
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var closed = false
    @Volatile private var inference: LlmInference? = null

    val isAvailable: Boolean
        get() = models.isInstalled(EddyModelCatalog.localLlm)

    suspend fun reply(
        message: String,
        memoryContext: String = "",
        evidence: String = "",
    ): String? = withContext(Dispatchers.Default) {
        if (closed || !isAvailable) return@withContext null
        mutex.withLock {
            if (closed) return@withLock null
            val engine = inference ?: createEngine() ?: return@withLock null
            val prompt = buildPrompt(message, memoryContext, evidence)
            runCatching { engine.generateResponse(prompt).trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
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
        val path = models.file(EddyModelCatalog.localLlm).absolutePath
        return runCatching {
            // En MediaPipe 0.10.24 topK/topP/temperature pertenecen a la sesión,
            // no a LlmInferenceOptions. El motor base usa sus valores seguros por defecto.
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(640)
                .build()
            LlmInference.createFromOptions(context.applicationContext, options).also { inference = it }
        }.getOrNull()
    }

    private fun buildPrompt(message: String, memoryContext: String, evidence: String): String = buildString {
        appendLine("Sos EDDY, un asistente personal masculino, local y privado en un teléfono Android.")
        appendLine("Respondé en español natural y breve, con tono nicaragüense ligero cuando sea apropiado.")
        appendLine("No inventés datos. Si hay evidencia web, basate solamente en ella y señalá incertidumbre.")
        appendLine("Tu identidad es EDDY y tu razonamiento conversacional ocurre en este teléfono.")
        appendLine("Priorizá la respuesta útil antes que explicaciones largas.")
        if (memoryContext.isNotBlank()) {
            appendLine("\nCONTEXTO LOCAL:")
            appendLine(memoryContext.take(5_000))
        }
        if (evidence.isNotBlank()) {
            appendLine("\nEVIDENCIA WEB RECUPERADA:")
            appendLine(evidence.take(7_000))
        }
        appendLine("\nUSUARIO: ${message.take(2_000)}")
        append("EDDY:")
    }
}
