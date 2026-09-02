package com.niko.assistant.localai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.niko.assistant.ai.NikoAiSettings
import com.niko.assistant.devicecontrol.NikoVisualContext
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
 * LEO prefiere Qwen2.5 1.5B INT8 en teléfonos con memoria suficiente y conserva
 * Qwen2.5 0.5B como ruta ligera. El runtime nativo solo se toca cuando el perfil del
 * dispositivo lo considera seguro; un SIGSEGV dentro de MediaPipe no puede atraparse
 * desde Kotlin y mataría también al servicio de voz.
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

    val runtimeSupported: Boolean
        get() = profile.supportsLocalLlm

    val isAvailable: Boolean
        get() = runtimeSupported && NikoModelCatalog.conversationModels.any(models::isInstalled)

    val preferredModel: NikoModelSpec
        get() = NikoModelCatalog.recommendedConversationModel(profile)

    /**
     * Carga el mejor modelo instalado sin generar tokens. En dispositivos bloqueados
     * por compatibilidad no crea ningún objeto LlmInference ni hilo nativo Drishti.
     */
    suspend fun prewarm(): Boolean = withContext(Dispatchers.Default) {
        if (closed || !runtimeSupported) return@withContext false
        val candidate = installedCandidates().firstOrNull() ?: return@withContext false
        mutex.withLock {
            if (closed || !runtimeSupported) false else engineFor(candidate) != null
        }
    }

    suspend fun reply(
        message: String,
        memoryContext: String = "",
        evidence: String = "",
    ): String? = withContext(Dispatchers.Default) {
        lastError = null
        if (closed) return@withContext null

        // Primero permitimos responder desde conocimiento local sin encender Qwen.
        val visualRequest = NikoVisualContext.wantsScreenContext(message)
        if (!visualRequest) {
            runCatching { knowledge.recall(message) }.getOrNull()?.let { learned ->
                return@withContext learned.answer
            }
        }

        // Importante: el crash observado es SIGSEGV nativo, no Exception. La única
        // recuperación segura en este proceso es no entrar al runtime incompatible.
        if (!runtimeSupported) {
            lastError = runtimeBlockedMessage()
            return@withContext null
        }

        // Contexto visual solo se captura una vez que sabemos que el runtime local puede
        // procesarlo. Así no retenemos una pantalla que después no podremos interpretar.
        val visual = if (visualRequest) NikoVisualContext.capture() else null
        visual?.problem?.let { problem ->
            lastError = problem
            return@withContext problem
        }

        val combinedEvidence = buildList {
            visual?.evidence?.takeIf { it.isNotBlank() }?.let(::add)
            evidence.takeIf { it.isNotBlank() }?.let(::add)
        }.joinToString("\n\n")

        val candidates = installedCandidates()
        if (candidates.isEmpty()) {
            if (visualRequest) {
                val messageForUser = "Puedo leer la pantalla localmente, pero primero prepará el modelo de conversación local en Ajustes para que pueda interpretarla."
                lastError = messageForUser
                return@withContext messageForUser
            }
            val quality = if (preferredModel == NikoModelCatalog.localLlmQuality) " de alta calidad" else " ligero"
            lastError = "Prepará el modelo local$quality de conversación en Ajustes."
            return@withContext null
        }

        mutex.withLock {
            if (closed || !runtimeSupported) return@withLock null
            for (spec in candidates) {
                val engine = engineFor(spec) ?: continue
                val prompt = LocalConversationPrompt.fit(
                    message,
                    memoryContext,
                    combinedEvidence,
                    NikoAiSettings.personality(appContext),
                    engine::sizeInTokens,
                ) ?: run {
                    lastError = "La consulta supera la capacidad del modelo local. Probá una pregunta más corta."
                    return@withLock if (visualRequest) lastError else null
                }

                val answer = runCatching { cleanAnswer(engine.generateResponse(prompt)) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                if (answer != null) {
                    lastError = null
                    return@withLock answer
                }

                releaseEngine()
            }

            lastError = if (visualRequest) {
                "No pude interpretar la pantalla localmente en este intento. Probá de nuevo con la aplicación visible."
            } else {
                "El modelo local no pudo responder. Cerré su memoria para recuperarlo en el siguiente intento."
            }
            if (visualRequest) lastError else null
        }
    }

    /**
     * Inferencia local para clasificadores/routers internos. En un fabricante marcado
     * como incompatible devuelve null para que el parser determinista siga solo, sin
     * arriesgar la vida del proceso principal.
     */
    suspend fun completeStructured(instruction: String): String? = withContext(Dispatchers.Default) {
        lastError = null
        if (closed || !runtimeSupported) {
            if (!runtimeSupported) lastError = runtimeBlockedMessage()
            return@withContext null
        }
        val candidates = installedCandidates()
        if (candidates.isEmpty()) return@withContext null

        mutex.withLock {
            if (closed || !runtimeSupported) return@withLock null
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
        if (!runtimeSupported) return emptyList()
        val recommended = preferredModel
        return (listOf(recommended) + NikoModelCatalog.conversationModels)
            .distinctBy { it.id }
            .filter(models::isInstalled)
    }

    private fun engineFor(spec: NikoModelSpec): LlmInference? {
        if (!runtimeSupported) return null
        val current = inference
        if (current != null && loadedSpec == spec) return current
        releaseEngine()
        return createEngine(spec)
    }

    private fun createEngine(spec: NikoModelSpec): LlmInference? {
        if (!runtimeSupported) return null
        val path = models.file(spec).absolutePath
        return runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(path)
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

    private fun runtimeBlockedMessage(): String =
        "El motor generativo local está en modo seguro para ${profile.manufacturer.ifBlank { "este dispositivo" }} ${profile.model}. LEO mantendrá la voz y las acciones locales sin cargar Qwen en este proceso."

    private fun structuredPrompt(body: String): String = buildString {
        appendLine("<|im_start|>system")
        appendLine("Sos un router interno de LEO. Seguí exactamente el formato solicitado por el sistema. No agregués explicaciones ni Markdown.")
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
        .replace(Regex("^(LEO|Leo|NIKO|Niko|Asistente)\\s*:\\s*"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    companion object {
        private const val STRUCTURED_MAX_INPUT_TOKENS = 1_080
    }
}
