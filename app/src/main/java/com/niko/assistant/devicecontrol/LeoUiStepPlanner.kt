package com.niko.assistant.devicecontrol

import java.util.Locale

/** Immutable description of the accessibility tree observed for one decision. */
data class LeoUiSnapshot(
    val packageName: String,
    val tree: String,
    val nodeCount: Int,
    val snapshotId: Long,
    val uiRevision: Long,
    val signature: String,
)

data class LeoUiActionResult(
    val success: Boolean,
    val message: String,
    val blocked: Boolean = false,
    val stale: Boolean = false,
)

enum class LeoUiAction {
    CLICK,
    LONG_CLICK,
    TYPE,
    CLEAR,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    SELECT,
    TOGGLE,
}

sealed interface LeoUiStep {
    data class Do(
        val action: LeoUiAction,
        val nodeId: String? = null,
        val text: String? = null,
        val desired: Boolean? = null,
    ) : LeoUiStep {
        fun describe(): String = buildList {
            add(action.name)
            nodeId?.let(::add)
            text?.take(100)?.let(::add)
            desired?.let { add(if (it) "ON" else "OFF") }
        }.joinToString("|")
    }

    data class Done(val message: String) : LeoUiStep
    data class Abort(val reason: String) : LeoUiStep
}

/**
 * Structured one-step planner for the currently visible Android UI.
 *
 * The model is allowed to choose only one operation over node ids from the current
 * snapshot. Parsing and node capability checks happen locally before the step reaches
 * AccessibilityService. No coordinate is part of this DSL.
 */
class LeoUiStepPlanner(
    private val structuredCompletion: suspend (String) -> String?,
) {
    @Volatile var lastError: String? = null
        private set

    suspend fun next(
        task: String,
        snapshot: LeoUiSnapshot,
        history: String = "",
    ): LeoUiStep {
        lastError = null
        if (snapshot.nodeCount <= 0 || snapshot.tree.isBlank()) {
            return LeoUiStep.Abort("No pude leer controles de la pantalla actual.")
        }
        val raw = runCatching { structuredCompletion(prompt(task, snapshot, history)) }.getOrNull()
        if (raw.isNullOrBlank()) {
            lastError = "No pude decidir el próximo paso de forma segura."
            return LeoUiStep.Abort(lastError.orEmpty())
        }
        return parse(raw, snapshot) ?: run {
            lastError = "El plan visual no fue válido para la pantalla actual."
            LeoUiStep.Abort(lastError.orEmpty())
        }
    }

    /** Public for deterministic tests; malformed or stale model output is rejected. */
    fun parse(value: String, snapshot: LeoUiSnapshot): LeoUiStep? {
        val raw = value.trim()
        if (raw.isBlank() || raw.startsWith("```") || raw.contains('\n') || raw.contains('\r')) return null
        if (raw.length > MAX_STEP_CHARS || raw.any { it == '\u0000' || it.isISOControl() }) return null

        val parts = raw.split('|', limit = 3).map(String::trim)
        val opcode = parts.firstOrNull()?.uppercase(Locale.ROOT) ?: return null
        fun node(index: Int): Pair<String, String>? {
            val id = parts.getOrNull(index)?.takeIf(NODE_REGEX::matches) ?: return null
            val line = snapshot.tree.lineSequence()
                .firstOrNull { it.trimStart().startsWith("[$id] ") }
                ?.trim()
                ?: return null
            if ("disabled" in line || "password-protected" in line || NikoUiTaskPolicy.isSensitiveControl(line)) return null
            return id to line
        }
        fun exactArity(size: Int) = parts.size == size

        return when (opcode) {
            "CLICK" -> if (exactArity(2)) node(1)?.first?.let { LeoUiStep.Do(LeoUiAction.CLICK, it) } else null
            "LONG_CLICK" -> if (exactArity(2)) node(1)?.takeIf { "long-clickable" in it.second }?.first
                ?.let { LeoUiStep.Do(LeoUiAction.LONG_CLICK, it) } else null
            "TYPE" -> {
                if (!exactArity(3)) return null
                val target = node(1)?.takeIf { "editable" in it.second } ?: return null
                val text = parts[2].takeIf { it.isNotBlank() && it.length <= MAX_TYPE_CHARS && !NikoUiTaskPolicy.isSensitiveControl(it) }
                    ?: return null
                LeoUiStep.Do(LeoUiAction.TYPE, target.first, text)
            }
            "CLEAR" -> if (exactArity(2)) node(1)?.takeIf { "editable" in it.second }?.first
                ?.let { LeoUiStep.Do(LeoUiAction.CLEAR, it) } else null
            "SCROLL_FORWARD" -> if (exactArity(2)) node(1)?.takeIf { "scrollable" in it.second }?.first
                ?.let { LeoUiStep.Do(LeoUiAction.SCROLL_FORWARD, it) } else null
            "SCROLL_BACKWARD" -> if (exactArity(2)) node(1)?.takeIf { "scrollable" in it.second }?.first
                ?.let { LeoUiStep.Do(LeoUiAction.SCROLL_BACKWARD, it) } else null
            "BACK" -> if (exactArity(1)) LeoUiStep.Do(LeoUiAction.BACK) else null
            "HOME" -> if (exactArity(1)) LeoUiStep.Do(LeoUiAction.HOME) else null
            "RECENTS" -> if (exactArity(1)) LeoUiStep.Do(LeoUiAction.RECENTS) else null
            "NOTIFICATIONS" -> if (exactArity(1)) LeoUiStep.Do(LeoUiAction.NOTIFICATIONS) else null
            "QUICK_SETTINGS" -> if (exactArity(1)) LeoUiStep.Do(LeoUiAction.QUICK_SETTINGS) else null
            "SELECT" -> if (exactArity(2)) node(1)?.takeIf { "selectable" in it.second }?.first
                ?.let { LeoUiStep.Do(LeoUiAction.SELECT, it) } else null
            "TOGGLE" -> {
                if (!exactArity(3)) return null
                val target = node(1)?.takeIf { "checkable" in it.second } ?: return null
                val desired = when (parts[2].uppercase(Locale.ROOT)) {
                    "ON", "TRUE", "1" -> true
                    "OFF", "FALSE", "0" -> false
                    else -> return null
                }
                LeoUiStep.Do(LeoUiAction.TOGGLE, target.first, desired = desired)
            }
            "DONE" -> if (parts.size in 1..2) LeoUiStep.Done(parts.getOrNull(1).orEmpty().take(MAX_MESSAGE_CHARS)) else null
            "ABORT" -> if (parts.size in 1..2) LeoUiStep.Abort(parts.getOrNull(1).orEmpty().take(MAX_MESSAGE_CHARS)) else null
            else -> null
        }
    }

    private fun prompt(task: String, snapshot: LeoUiSnapshot, history: String): String = """
        Sos el planificador visual estructurado de LEO para Android.
        Objetivo del usuario: ${task.replace('\n', ' ').take(600)}
        Aplicación visible: ${snapshot.packageName.take(180)}
        Snapshot actual: ${snapshot.snapshotId}
        Revisión UI: ${snapshot.uiRevision}
        Firma UI: ${snapshot.signature}

        Elegí EXACTAMENTE UN siguiente paso. Respondé una sola línea, sin Markdown ni explicación.
        DSL permitido:
        CLICK|node_id
        LONG_CLICK|node_id
        TYPE|node_id|texto
        CLEAR|node_id
        SCROLL_FORWARD|node_id
        SCROLL_BACKWARD|node_id
        BACK
        HOME
        RECENTS
        NOTIFICATIONS
        QUICK_SETTINGS
        SELECT|node_id
        TOGGLE|node_id|ON u OFF
        DONE|mensaje breve
        ABORT|motivo breve

        Reglas obligatorias:
        - Solo podés usar node_id que aparecen en ESTE árbol. Nunca inventés ids ni coordenadas.
        - Hacé un solo paso. LEO observará de nuevo antes de decidir el siguiente.
        - Si una etiqueta o posición cambió, razoná únicamente con este snapshot actual.
        - TYPE solo sobre campos editables; SCROLL solo sobre nodos scrollable.
        - SELECT solo sobre nodos selectable y TOGGLE solo sobre nodos checkable.
        - No escribás ni revelés contraseñas, PIN, códigos 2FA o códigos de verificación.
        - No confirmés pagos, transferencias, compras, publicaciones, mensajes, permisos críticos,
          cambios de seguridad, borrados de cuenta, restablecimientos ni desinstalaciones.
        - Podés navegar hasta páginas como Batería, Apps, Permisos o Seguridad, pero no aprobar
          permisos ni efectuar cambios sensibles dentro de ellas.
        - En WhatsApp podés buscar y abrir un chat, pero nunca tocar Enviar.
        - En Spotify, YouTube y navegador podés buscar y abrir contenido seguro.
        - Si el objetivo ya se cumplió, devolvé DONE. Si no identificás un próximo paso razonable, ABORT.

        Historial reciente:
        ${history.takeLast(2_200).ifBlank { "ninguno" }}

        Árbol accesible ACTUAL:
        ${snapshot.tree.take(MAX_TREE_CHARS)}
    """.trimIndent()

    private companion object {
        val NODE_REGEX = Regex("node_\\d{1,5}")
        const val MAX_STEP_CHARS = 1_800
        const val MAX_TYPE_CHARS = 1_500
        const val MAX_MESSAGE_CHARS = 180
        const val MAX_TREE_CHARS = 10_000
    }
}
