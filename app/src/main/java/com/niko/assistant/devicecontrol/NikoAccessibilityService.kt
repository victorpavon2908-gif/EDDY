package com.niko.assistant.devicecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Capa observable de automatización de NIKO.
 *
 * Inspirada en el patrón observar -> actuar -> volver a observar usado por asistentes
 * Android con Accessibility, pero implementada dentro de la arquitectura nativa de NIKO.
 * El usuario siempre debe habilitar manualmente el servicio en Ajustes de Android.
 */
open class NikoAccessibilityService : AccessibilityService() {
    data class UiSnapshot(
        val packageName: String,
        val tree: String,
        val nodeCount: Int,
    )

    data class UiActionResult(
        val success: Boolean,
        val message: String,
        val blocked: Boolean = false,
    )

    private val nodeMap = ConcurrentHashMap<String, AccessibilityNodeInfo>()
    private val nodeCounter = AtomicInteger(0)

    @Volatile private var currentPackageName: String = ""

    override fun onServiceConnected() {
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.packageName?.toString()?.takeIf(String::isNotBlank)?.let { currentPackageName = it }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        clearNodeMap()
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun openRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    fun openQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    /**
     * Captura compacta de la interfaz actual. Cada nodo obtiene un id efímero que solo
     * es válido hasta la siguiente captura. Se limita el árbol para no saturar el LLM.
     */
    fun snapshot(maxNodes: Int = 140, maxDepth: Int = 12): UiSnapshot {
        val attempts = longArrayOf(0L, 70L, 160L, 320L)
        var last = UiSnapshot(currentPackageName, "No hay una ventana activa.", 0)

        for (delayMs in attempts) {
            if (delayMs > 0L) Thread.sleep(delayMs)
            clearNodeMap()
            val root = rootInActiveWindow ?: continue
            val pkg = root.packageName?.toString()?.takeIf(String::isNotBlank) ?: currentPackageName
            currentPackageName = pkg
            val out = StringBuilder("Package: $pkg\n")
            try {
                appendNode(root, out, 0, maxDepth.coerceIn(2, 20), maxNodes.coerceIn(20, 240))
            } finally {
                runCatching { root.recycle() }
            }
            last = UiSnapshot(pkg, out.toString().take(MAX_TREE_CHARS), nodeMap.size)
            if (last.nodeCount > 0) return last
        }
        return last
    }

    fun performNodeAction(action: String, nodeId: String? = null, text: String? = null): UiActionResult {
        return when (action.lowercase()) {
            "back" -> UiActionResult(goBack(), "Volví a la pantalla anterior.")
            "home" -> UiActionResult(goHome(), "Volví al inicio del teléfono.")
            "recents" -> UiActionResult(openRecents(), "Abrí aplicaciones recientes.")
            "notifications" -> UiActionResult(openNotifications(), "Abrí las notificaciones.")
            "quick_settings" -> UiActionResult(openQuickSettings(), "Abrí los ajustes rápidos.")
            "scroll_forward" -> nodeAction(nodeId) { node ->
                val ok = scrollNodeForward(node)
                UiActionResult(ok, if (ok) "Desplacé la pantalla hacia adelante." else "No encontré dónde desplazar.")
            }
            "scroll_backward" -> nodeAction(nodeId) { node ->
                val ok = scrollNodeBackward(node)
                UiActionResult(ok, if (ok) "Desplacé la pantalla hacia atrás." else "No encontré dónde desplazar.")
            }
            "click" -> nodeAction(nodeId) { node -> clickMappedNode(nodeId.orEmpty(), node) }
            "type" -> nodeAction(nodeId) { node -> typeIntoNode(nodeId.orEmpty(), node, text.orEmpty()) }
            "clear" -> nodeAction(nodeId) { node -> typeIntoNode(nodeId.orEmpty(), node, "") }
            else -> UiActionResult(false, "Acción de interfaz no reconocida: $action")
        }
    }

    fun clickText(text: String): Boolean {
        val query = text.trim()
        if (query.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        return try {
            root.findAccessibilityNodeInfosByText(query).orEmpty().any { node ->
                !node.isPassword && !isHighRiskNode(node) && clickNodeOrParent(node)
            }
        } finally {
            runCatching { root.recycle() }
        }
    }

    fun setTextInFocusedField(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            if (!focused.isEditable || focused.isPassword) return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            runCatching { root.recycle() }
        }
    }

    fun scrollForward(): Boolean = rootInActiveWindow?.let(::scrollNodeForward) == true
    fun scrollBackward(): Boolean = rootInActiveWindow?.let(::scrollNodeBackward) == true

    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 70L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun appendNode(
        node: AccessibilityNodeInfo,
        out: StringBuilder,
        depth: Int,
        maxDepth: Int,
        maxNodes: Int,
    ) {
        if (nodeMap.size >= maxNodes || depth > maxDepth) return
        // Nunca exponer el propio overlay/ventana de NIKO al agente visual.
        val pkg = node.packageName?.toString().orEmpty()
        if (pkg == packageName || pkg == "com.eddy.assistant") return

        val id = "node_${nodeCounter.getAndIncrement()}"
        runCatching { nodeMap[id] = AccessibilityNodeInfo.obtain(node) }

        val attrs = mutableListOf<String>()
        val className = node.className?.toString()?.substringAfterLast('.') ?: "View"
        val protected = node.isPassword
        val nodeText = if (protected) "" else node.text?.toString()?.replace(Regex("\\s+"), " ")?.take(100).orEmpty()
        val desc = if (protected) "" else node.contentDescription?.toString()?.replace(Regex("\\s+"), " ")?.take(100).orEmpty()
        val res = node.viewIdResourceName?.substringAfterLast('/')?.take(80).orEmpty()
        if (nodeText.isNotBlank()) attrs += "text=\"${escape(nodeText)}\""
        if (desc.isNotBlank() && desc != nodeText) attrs += "desc=\"${escape(desc)}\""
        if (res.isNotBlank()) attrs += "id=\"${escape(res)}\""
        if (node.isClickable) attrs += "clickable"
        if (node.isEditable) attrs += "editable"
        if (node.isScrollable) attrs += "scrollable"
        if (node.isChecked) attrs += "checked"
        if (node.isSelected) attrs += "selected"
        if (!node.isEnabled) attrs += "disabled"
        if (protected) attrs += "password-protected"
        out.append("  ".repeat(depth)).append('[').append(id).append("] ").append(className)
        if (attrs.isNotEmpty()) out.append(" [").append(attrs.joinToString(", ")).append(']')
        out.append('\n')

        for (i in 0 until node.childCount) {
            if (nodeMap.size >= maxNodes) break
            val child = node.getChild(i) ?: continue
            try { appendNode(child, out, depth + 1, maxDepth, maxNodes) }
            finally { runCatching { child.recycle() } }
        }
    }

    private inline fun nodeAction(nodeId: String?, block: (AccessibilityNodeInfo) -> UiActionResult): UiActionResult {
        if (nodeId.isNullOrBlank()) return UiActionResult(false, "Falta el nodo de interfaz.")
        val node = nodeMap[nodeId] ?: return UiActionResult(false, "La pantalla cambió; necesito observarla de nuevo.")
        return block(node)
    }

    private fun clickMappedNode(id: String, node: AccessibilityNodeInfo): UiActionResult {
        if (node.isPassword || isHighRiskNode(node)) {
            return UiActionResult(false, "Bloqueé un control sensible. Esa acción necesita interacción directa del usuario.", blocked = true)
        }
        if (!node.isEnabled) return UiActionResult(false, "El control $id está deshabilitado.")

        if (clickNodeOrParent(node)) return UiActionResult(true, "Toqué ${nodeLabel(node, id)}.")

        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.width() <= 0 || rect.height() <= 0) return UiActionResult(false, "No pude ubicar $id en pantalla.")
        val ok = tap(rect.exactCenterX(), rect.exactCenterY())
        return UiActionResult(ok, if (ok) "Toqué ${nodeLabel(node, id)} con gesto." else "No pude tocar $id.")
    }

    private fun typeIntoNode(id: String, node: AccessibilityNodeInfo, value: String): UiActionResult {
        if (!node.isEditable) return UiActionResult(false, "$id no es un campo editable.")
        if (node.isPassword) return UiActionResult(false, "NIKO no escribe contraseñas mediante automatización.", blocked = true)
        if (value.length > 1_500) return UiActionResult(false, "El texto es demasiado largo para automatizar.")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return UiActionResult(ok, if (ok) "Escribí en ${nodeLabel(node, id)}." else "No pude escribir en $id.")
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var depth = 0
        while (current != null && depth < 7) {
            if (current.isPassword || isHighRiskNode(current)) return false
            if (current.isClickable && current.isEnabled && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            current = current.parent
            depth++
        }
        return false
    }

    private fun scrollNodeForward(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { if (scrollNodeForward(child)) return true }
            finally { runCatching { child.recycle() } }
        }
        return false
    }

    private fun scrollNodeBackward(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try { if (scrollNodeBackward(child)) return true }
            finally { runCatching { child.recycle() } }
        }
        return false
    }

    private fun isHighRiskNode(node: AccessibilityNodeInfo): Boolean {
        val value = listOf(node.text, node.contentDescription, node.viewIdResourceName)
            .joinToString(" ") { it?.toString().orEmpty() }
            .lowercase()
        return HIGH_RISK.any(value::contains)
    }

    private fun nodeLabel(node: AccessibilityNodeInfo, fallback: String): String {
        return node.text?.toString()?.takeIf(String::isNotBlank)
            ?: node.contentDescription?.toString()?.takeIf(String::isNotBlank)
            ?: node.viewIdResourceName?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: fallback
    }

    private fun escape(value: String): String = value.replace('"', '\'').replace('|', ' ')

    private fun clearNodeMap() {
        nodeMap.values.forEach { runCatching { it.recycle() } }
        nodeMap.clear()
        nodeCounter.set(0)
    }

    companion object {
        private const val MAX_TREE_CHARS = 10_000
        private val HIGH_RISK = listOf(
            "transferir", "transfer", "enviar dinero", "send money", "pagar", "payment",
            "comprar", "purchase", "confirmar compra", "delete account", "eliminar cuenta",
            "borrar cuenta", "factory reset", "restablecer de fabrica", "desinstalar",
        )

        @Volatile
        var instance: NikoAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }
}
