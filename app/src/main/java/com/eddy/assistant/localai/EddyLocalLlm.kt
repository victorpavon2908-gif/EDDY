package com.eddy.assistant.localai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
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
    @Volatile private var inference: LlmInference? = null

    val isAvailable: Boolean
        get() = models.isInstalled(EddyModelCatalog.localLlm)

    suspend fun reply(
        message: String,
        memoryContext: String = "",
        evidence: String = "",
    ): String? = withContext(Dispatchers.Default) {
        if (!isAvailable) return@withContext null
        mutex.withLock {
            val engine = inference ?: createEngine() ?: return@withLock null
            val prompt = buildPrompt(message, memoryContext, evidence)
            runCatching { engine.generateResponse(prompt).trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    }

    fun release() {
        runCatching { inference?.close() }
        inference = null
    }

    private fun createEngine(): LlmInference? {
        val path = models.file(EddyModelCatalog.localLlm).absolutePath
        return runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                .setMaxTokens(640)
                .setTopK(40)
                .setTopP(0.9f)
                .setTemperature(0.45f)
                .build()
            LlmInference.createFromOptions(context.applicationContext, options).also { inference = it }
        }.getOrNull()
    }

    private fun buildPrompt(message: String, memoryContext: String, evidence: String): String = buildString {
        appendLine("Sos EDDY, un asistente personal masculino, local y privado en un teléfono Android.")
        appendLine("Respondé en español natural y breve, con tono nicaragüense ligero cuando sea apropiado.")
        appendLine("No inventés datos. Si hay evidencia web, basate solamente en ella y señalá incertidumbre.")
        appendLine("No digás que sos ChatGPT, OpenAI ni un servicio remoto. Tu identidad es EDDY.")
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
