package com.niko.assistant.devicecontrol

import com.niko.assistant.localai.NikoLocalLlm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Agente visual local de NIKO.
 *
 * Patrón: observar UI -> pedir una sola acción estructurada -> ejecutarla -> observar de
 * nuevo. El modelo nunca recibe una API arbitraria: solo puede elegir un DSL cerrado.
 */
class NikoUiAutomationAgent(
    private val localLlm: NikoLocalLlm,
) {
    data class Result(
        val success: Boolean,
        val message: String,
        val iterations: Int,
    )

    suspend fun run(task: String): Result {
        val request = task.trim().take(600)
        if (request.isBlank()) return Result(false, "No recibí una tarea para la aplicación.", 0)
        val service = NikoAccessibilityService.instance
            ?: return Result(false, "Activá NIKO Device Control en Accesibilidad de Android para que pueda manejar pantallas de otras apps.", 0)
        if (!localLlm.isAvailable) {
            return Result(false, "Prepará la conversación local en Ajustes para usar el control inteligente de aplicaciones.", 0)
        }

        val history = mutableListOf<String>()
        repeat(MAX_ITERATIONS) { index ->
            val snapshot = withContext(Dispatchers.Default) { service.snapshot() }
            if (snapshot.nodeCount == 0) {
                return Result(false, "No pude leer controles de ${snapshot.packageName.ifBlank { "la aplicación" }}.", index + 1)
            }

            val raw = localLlm.completeStructured(
                prompt(
                    task = request,
                    packageName = snapshot.packageName,
                    tree = snapshot.tree,
                    history = history.joinToString("\n").takeLast(1_800),
                ),
            ) ?: return Result(false, localLlm.lastError ?: "El modelo local no pudo decidir el siguiente paso.", index + 1)

            val action = parse(raw)
                ?: return Result(false, "No pude interpretar de forma segura el siguiente paso de la automatización.", index + 1)

            when (action) {
                is Step.Done -> return Result(true, action.message.ifBlank { "Listo." }, index + 1)
                is Step.Abort -> return Result(false, action.reason.ifBlank { "Detuve la automatización." }, index + 1)
                is Step.Do -> {
                    val result = withContext(Dispatchers.Main) {
                        service.performNodeAction(action.action, action.nodeId, action.text)
                    }
                    history += "${action.describe()} -> ${result.message}"
                    if (result.blocked) return Result(false, result.message, index + 1)
                    if (!result.success && index >= 2) {
                        return Result(false, "No logré completar la tarea. ${result.message}", index + 1)
                    }
                    delay(ACTION_SETTLE_MS)
                }
            }
        }

        return Result(false, "La tarea necesitó demasiados pasos y la detuve para no tocar controles de más.", MAX_ITERATIONS)
    }

    private fun prompt(task: String, packageName: String, tree: String, history: String): String = """
        Sos el controlador visual local de NIKO para Android.
        Objetivo del usuario: $task
        Aplicación visible: $packageName

        Elegí EXACTAMENTE un siguiente paso. Respondé una sola línea, sin Markdown ni explicación.
        Acciones permitidas:
        CLICK|node_id
        TYPE|node_id|texto
        CLEAR|node_id
        SCROLL_FORWARD|node_id
        SCROLL_BACKWARD|node_id
        BACK
        HOME
        DONE|mensaje breve para el usuario
        ABORT|motivo breve

        Reglas:
        - Usá solamente node_id presentes en el árbol actual.
        - No escribás contraseñas, PIN, códigos 2FA ni datos secretos.
        - No confirmés compras, pagos, transferencias, borrados de cuenta, desinstalaciones ni cambios de seguridad.
        - Si el objetivo ya está cumplido, devolvé DONE.
        - Si no hay un control razonable para continuar, devolvé ABORT.
        - Después de un click NIKO volverá a observar la pantalla, así que hacé un solo paso.

        Historial de pasos:
        ${history.ifBlank { "ninguno" }}

        Árbol accesible actual:
        ${tree.take(9_000)}
    """.trimIndent()

    private fun parse(value: String): Step? {
        val line = value.lineSequence().map(String::trim).firstOrNull(String::isNotBlank)
            ?.replace("```", "")?.trim() ?: return null
        val parts = line.split('|', limit = 3).map(String::trim)
        return when (parts.firstOrNull()?.uppercase()) {
            "CLICK" -> node(parts.getOrNull(1))?.let { Step.Do("click", it) }
            "TYPE" -> {
                val id = node(parts.getOrNull(1)) ?: return null
                val text = parts.getOrNull(2)?.replace(Regex("[\\r\\n]+"), " ")?.take(1_500) ?: return null
                Step.Do("type", id, text)
            }
            "CLEAR" -> node(parts.getOrNull(1))?.let { Step.Do("clear", it) }
            "SCROLL_FORWARD" -> node(parts.getOrNull(1))?.let { Step.Do("scroll_forward", it) }
            "SCROLL_BACKWARD" -> node(parts.getOrNull(1))?.let { Step.Do("scroll_backward", it) }
            "BACK" -> Step.Do("back", null)
            "HOME" -> Step.Do("home", null)
            "DONE" -> Step.Done(parts.getOrNull(1).orEmpty().take(180))
            "ABORT" -> Step.Abort(parts.getOrNull(1).orEmpty().take(180))
            else -> null
        }
    }

    private fun node(value: String?): String? = value
        ?.trim()
        ?.takeIf { NODE_REGEX.matches(it) }

    private sealed interface Step {
        data class Do(val action: String, val nodeId: String?, val text: String? = null) : Step {
            fun describe(): String = listOfNotNull(action, nodeId, text?.take(80)).joinToString("|")
        }
        data class Done(val message: String) : Step
        data class Abort(val reason: String) : Step
    }

    companion object {
        private val NODE_REGEX = Regex("node_\\d{1,4}")
        private const val MAX_ITERATIONS = 7
        private const val ACTION_SETTLE_MS = 420L
    }
}
