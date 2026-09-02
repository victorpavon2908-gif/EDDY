package com.niko.assistant.localai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.learning.NikoKnowledgeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Cerebro generativo completamente local.
 *
 * NIKO prefiere Qwen2.5 1.5B INT8 en teléfonos con memoria suficiente y conserva
 * Qwen2.5 0.5B como ruta ligera. Ninguno se descarga durante el arranque del micrófono:
 * el usuario prepara el modelo desde Ajustes y después la inferencia no usa Internet.
 */
class NikoLocalLlm(
    private val context: Context,
    private val models: NikoModelManager,
) {
    private val appContext = context.applicationContext
    private val profile = NikoDeviceProfile.detect(appContext)
    private val mutex = Mutex()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val knowledge = NikoKnowledgeStore(appContext)

    @Volatile private var closed = false
    @Volatile private var inference: LlmInference? = null
    @Volatile private var loadedSpec: NikoModelSpec? = null
    @Volatile var lastError: String? = null
        private set

    val isAvailable: Boolean
        get() = NikoModelCatalog.conversationModels.any(models::isInstalled)

    val preferredModel: NikoModelSpec
        get() = NikoModelCatalog.recommendedConversationModel(profile)

    suspend fun reply(
        message: String,
        memoryContext: String = "",
        evidence: String = "",
    ): String? = withContext(Dispatchers.Default) {
        lastError = null
        if (closed) return@withContext null

        // Conocimiento guardado puede resolver una consulta sin encender el LLM.
        runCatching { knowledge.recall(message) }.getOrNull()?.let { learned ->
            return@withContext learned.answer
        }

        val candidates = installedCandidates()
        if (candidates.isEmpty()) {
            val quality = if (preferredModel == NikoModelCatalog.localLlmQuality) " de alta calidad" else " ligero"
            lastError = "Prepará el modelo local$quality de conversación en Ajustes."
            return@withContext null
        }

        mutex.withLock {
            if (closed) return@withLock null
            for (spec in candidates) {
                val engine = engineFor(spec) ?: continue
                val prompt = LocalConversationPrompt.fit(
                    message,
                    memoryContext,
                    evidence,
                    NikoAiSettings.personality(appContext),
                    engine::sizeInTokens,
                ) ?: run {
                    lastError = "La consulta supera la capacidad del modelo local. Probá una pregunta más corta."
                    return@withLock null
                }

                val answer = runCatching { cleanAnswer(engine.generateResponse(prompt)) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                if (answer != null) {
                    lastError = null
                    return@withLock answer
                }

                // Si el modelo grande falla por presión de memoria, liberarlo permite
                // probar el modelo rápido ya instalado sin reiniciar todo el asistente.
                releaseEngine()
            }

            lastError = "El modelo local no pudo responder. Cerré su memoria para recuperarlo en el siguiente intento."
            null
        }
    }

    /**
     * Inferencia local para clasificadores/routers internos. No usa memoria aprendida,
     * Groq ni Internet y conserva saltos de línea para poder validar un DSL cerrado.
     */
    suspend fun completeStructured(instruction: String): String? = withContext(Dispatchers.Default) {
        lastError = null
        if (closed) return@withContext null
        val candidates = installedCandidates()
        if (candidates.isEmpty()) return@withContext null

        mutex.withLock {
            if (closed) return@withLock null
            for (spec in candidates) {
                val engine = engineFor(spec) ?: continue
                var body = instruction.trim().take(4_500)
                var prompt = structuredPrompt(body)
                repeat(5) {
                    val size = runCatching { engine.sizeInTokens(prompt) }.getOrDefault(Int.MAX_VALUE)
                    if (size in 1..STRUCTURED_MAX_INPUT_TOKENS) {
                        val result = runCatching { cleanStructured(engine.generateResponse(prompt)) }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                        if (result != null) {
                            lastError = null
                            return@withLock result
                        }
                        return@repeat
                    }
                    body = body.take((body.length * 3 / 4).coerceAtLeast(700))
                    prompt = structuredPrompt(body)
                }
                releaseEngine()
            }
            null
        }
    }

    fun release() {
        closed = true
        cleanupScope.launch {
            mutex.withLock { releaseEngine() }
            cleanupScope.cancel()
        }
    }

    private fun installedCandidates(): List<NikoModelSpec> {
        val recommended = preferredModel
        return (listOf(recommended) + NikoModelCatalog.conversationModels)
            .distinctBy { it.id }
            .filter(models::isInstalled)
    }

    private fun engineFor(spec: NikoModelSpec): LlmInference? {
        val current = inference
        if (current != null && loadedSpec == spec) return current
        releaseEngine()
        return createEngine(spec)
    }

    private fun createEngine(spec: NikoModelSpec): LlmInference? {
        val path = models.file(spec).absolutePath
        return runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
                // Ambos paquetes usan KV 1280: reservamos contexto suficiente y respuestas breves.
                .setMaxTokens(LocalConversationPrompt.MODEL_TOKENS)
                .build()
            LlmInference.createFromOptions(appContext, options).also {
                inference = it
                loadedSpec = spec
            }
        }.onFailure {
            lastError = "No pude iniciar ${spec.id}. Comprobá memoria disponible o prepará el modelo ligero."
        }.getOrNull()
    }

    private fun releaseEngine() {
        runCatching { inference?.close() }
        inference = null
        loadedSpec = null
    }

    private fun structuredPrompt(body: String): String = buildString {
        appendLine("<|im_start|>system")
        appendLine("Sos un router interno de NIKO. Seguí exactamente el formato solicitado por el usuario del sistema. No agregués explicaciones ni Markdown.")
        appendLine("<|im_end|>")
        appendLine("<|im_start|>user")
        appendLine(body)
        appendLine("<|im_end|>")
        append("<|im_start|>assistant\n")
    }

    private fun cleanStructured(value: String): String = value
        .replace("<|im_end|>", "")
        .replace("<|endoftext|>", "")
        .replace("<|im_start|>assistant", "")
        .replace("```text", "")
        .replace("```", "")
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")
        .trim()

    private fun cleanAnswer(value: String): String = value
        .replace("<|im_end|>", "")
        .replace("<|endoftext|>", "")
        .replace(Regex("^(NIKO|Niko|Asistente)\\s*:\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val STRUCTURED_MAX_INPUT_TOKENS = 1_080
    }
}
