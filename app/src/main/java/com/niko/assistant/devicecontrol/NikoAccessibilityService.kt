package com.niko.assistant.devicecontrol

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Observable accessibility layer for LEO.
 *
 * Every node id belongs to exactly one snapshot. Planned actions must present the
 * snapshot id and UI revision they were based on; if Android changed the screen in the
 * meantime, the action is rejected before touching anything and the agent must observe again.
 */
open class NikoAccessibilityService : AccessibilityService() {
    private val nodeMap = ConcurrentHashMap<String, AccessibilityNodeInfo>()
    private val nodeCounter = AtomicInteger(0)
    private val snapshotCounter = AtomicLong(0L)
    private val uiRevision = AtomicLong(0L)

    @Volatile private var currentPackageName: String = ""
    @Volatile private var activeSnapshotId: Long = 0L
    @Volatile private var activeSnapshotRevision: Long = -1L

    override fun onServiceConnected() {
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
        uiRevision.incrementAndGet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        event.packageName?.toString()?.takeIf(String::isNotBlank)?.let { currentPackageName = it }
        if (event.eventType in REVISION_EVENTS) uiRevision.incrementAndGet()
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
     * Captures the current UI into a bounded tree. Node ids are ephemeral and are valid
     * only for the returned snapshot id/revision pair.
     */
    fun snapshot(maxNodes: Int = 160, maxDepth: Int = 14): LeoUiSnapshot {
        val attempts = longArrayOf(0L, 70L, 160L, 320L)
        var last = LeoUiSnapshot(
            packageName = currentPackageName,
            tree = "No hay una ventana activa.",
            nodeCount = 0,
            snapshotId = snapshotCounter.incrementAndGet(),
            uiRevision = uiRevision.get(),
            signature = "empty",
        )

        for (delayMs in attempts) {
            if (delayMs > 0L) Thread.sleep(delayMs)
            clearNodeMap()
            val revisionBefore = uiRevision.get()
            val root = rootInActiveWindow ?: continue
            val pkg = root.packageName?.toString()?.takeIf(String::isNotBlank) ?: currentPackageName
            currentPackageName = pkg
            val out = StringBuilder("Package: $pkg\n")
            try {
                appendNode(root, out, 0, maxDepth.coerceIn(2, 20), maxNodes.coerceIn(20, 260))
            } finally {
                runCatching { root.recycle() }
            }

            val revisionAfter = uiRevision.get()
            val tree = out.toString().take(MAX_TREE_CHARS)
            if (revisionBefore != revisionAfter) {
                clearNodeMap()
                last = LeoUiSnapshot(
                    packageName = pkg,
                    tree = tree,
                    nodeCount = 0,
                    snapshotId = snapshotCounter.incrementAndGet(),
                    uiRevision = revisionAfter,
                    signature = stableSignature(pkg, tree),
                )
                continue
            }

            val id = snapshotCounter.incrementAndGet()
            activeSnapshotId = id
            activeSnapshotRevision = revisionAfter
            last = LeoUiSnapshot(
                packageName = pkg,
                tree = tree,
                nodeCount = nodeMap.size,
                snapshotId = id,
                uiRevision = revisionAfter,
                signature = stableSignature(pkg, tree),
            )
            if (last.nodeCount > 0) return last
        }
        return last
    }

    fun performNodeAction(
        action: String,
        nodeId: String? = null,
        text: String? = null,
        desired: Boolean? = null,
        expectedSnapshotId: Long = activeSnapshotId,
        expectedRevision: Long = activeSnapshotRevision,
    ): LeoUiActionResult {
        if (!isSnapshotCurrent(expectedSnapshotId, expectedRevision)) {
            return LeoUiActionResult(
                success = false,
                message = "La pantalla cambió; voy a observarla de nuevo antes de tocar nada.",
                stale = true,
            )
        }

        val result = when (action.lowercase()) {
            "back" -> LeoUiActionResult(goBack(), "Volví a la pantalla anterior.")
            "home" -> LeoUiActionResult(goHome(), "Volví al inicio del teléfono.")
            "recents" -> LeoUiActionResult(openRecents(), "Abrí aplicaciones recientes.")
            "notifications" -> LeoUiActionResult(openNotifications(), "Abrí las notificaciones.")
            "quick_settings" -> LeoUiActionResult(openQuickSettings(), "Abrí los ajustes rápidos.")
            "scroll_forward" -> nodeAction(nodeId) { node ->
                val ok = scrollNodeForward(node)
                LeoUiActionResult(ok, if (ok) "Desplacé la pantalla hacia adelante." else "No encontré dónde desplazar.")
            }
            "scroll_backward" -> nodeAction(nodeId) { node ->
                val ok = scrollNodeBackward(node)
                LeoUiActionResult(ok, if (ok) "Desplacé la pantalla hacia atrás." else "No encontré dónde desplazar.")
            }
            "click" -> nodeAction(nodeId) { node -> clickMappedNode(nodeId.orEmpty(), node) }
            "long_click" -> nodeAction(nodeId) { node -> longClickMappedNode(nodeId.orEmpty(), node) }
            "type" -> nodeAction(nodeId) { node -> typeIntoNode(nodeId.orEmpty(), node, text.orEmpty()) }
            "clear" -> nodeAction(nodeId) { node -> typeIntoNode(nodeId.orEmpty(), node, "") }
            "select" -> nodeAction(nodeId) { node -> selectMappedNode(nodeId.orEmpty(), node) }
            "toggle" -> nodeAction(nodeId) { node -> toggleMappedNode(nodeId.orEmpty(), node, desired) }
            else -> LeoUiActionResult(false, "Acción de interfaz no reconocida: $action")
        }

        if (result.success) invalidateSnapshot()
        return result
    }

    fun clickText(text: String): Boolean {
        val query = text.trim()
        if (query.isBlank()) return false
        val root = rootInActiveWindow ?: return false
        return try {
            val success = root.findAccessibilityNodeInfosByText(query).orEmpty().any { node ->
                !node.isPassword && !isHighRiskNode(node) && clickNodeOrParent(node)
            }
            if (success) uiRevision.incrementAndGet()
            success
        } finally {
            runCatching { root.recycle() }
        }
    }

    fun setTextInFocusedField(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            if (!focused.isEditable || focused.isPassword || isHighRiskNode(focused)) return false
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value.take(MAX_TYPE_CHARS))
            }
            val success = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (success) uiRevision.incrementAndGet()
            success
        } finally {
            runCatching { root.recycle() }
        }
    }

    fun scrollForward(): Boolean = withRoot { root -> scrollNodeForward(root) }
    fun scrollBackward(): Boolean = withRoot { root -> scrollNodeBackward(root) }

    private fun appendNode(
        node: AccessibilityNodeInfo,
        out: StringBuilder,
        depth: Int,
        maxDepth: Int,
        maxNodes: Int,
    ) {
        if (nodeMap.size >= maxNodes || depth > maxDepth) return
        val pkg = node.packageName?.toString().orEmpty()
        if (pkg == packageName || pkg == "com.eddy.assistant") return

        val id = "node_${nodeCounter.getAndIncrement()}"
        runCatching { nodeMap[id] = AccessibilityNodeInfo.obtain(node) }

        val attrs = mutableListOf<String>()
        val className = node.className?.toString()?.substringAfterLast('.') ?: "View"
        val protected = node.isPassword
        val nodeText = if (protected) "" else node.text?.toString()?.replace(Regex("\\s+"), " ")?.take(120).orEmpty()
        val desc = if (protected) "" else node.contentDescription?.toString()?.replace(Regex("\\s+"), " ")?.take(120).orEmpty()
        val res = node.viewIdResourceName?.substringAfterLast('/')?.take(100).orEmpty()
        if (nodeText.isNotBlank()) attrs += "text=\"${escape(nodeText)}\""
        if (desc.isNotBlank() && desc != nodeText) attrs += "desc=\"${escape(desc)}\""
        if (res.isNotBlank()) attrs += "id=\"${escape(res)}\""
        if (node.isClickable) attrs += "clickable"
        if (node.isLongClickable) attrs += "long-clickable"
        if (node.isEditable) attrs += "editable"
        if (node.isScrollable) attrs += "scrollable"
        if (node.isCheckable) attrs += "checkable"
        if (node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SELECT }) attrs += "selectable"
        if (node.isChecked) attrs += "checked"
        if (node.isSelected) attrs += "selected"
        if (node.isFocused) attrs += "focused"
        if (!node.isEnabled) attrs += "disabled"
        if (protected) attrs += "password-protected"
        out.append("  ".repeat(depth)).append('[').append(id).append("] ").append(className)
        if (attrs.isNotEmpty()) out.append(" [").append(attrs.joinToString(", ")).append(']')
        out.append('\n')

        for (i in 0 until node.childCount) {
            if (nodeMap.size >= maxNodes) break
            val child = node.getChild(i) ?: continue
            try {
                appendNode(child, out, depth + 1, maxDepth, maxNodes)
            } finally {
                runCatching { child.recycle() }
            }
        }
    }

    private inline fun nodeAction(nodeId: String?, block: (AccessibilityNodeInfo) -> LeoUiActionResult): LeoUiActionResult {
        if (nodeId.isNullOrBlank()) return LeoUiActionResult(false, "Falta el nodo de interfaz.")
        val node = nodeMap[nodeId]
            ?: return LeoUiActionResult(false, "La pantalla cambió; necesito observarla de nuevo.", stale = true)
        return block(node)
    }

    private fun clickMappedNode(id: String, node: AccessibilityNodeInfo): LeoUiActionResult {
        if (node.isPassword || isHighRiskNode(node)) return blockedSensitive()
        if (!node.isEnabled) return LeoUiActionResult(false, "El control $id está deshabilitado.")
        val ok = clickNodeOrParent(node)
        return LeoUiActionResult(
            ok,
            if (ok) "Toqué ${nodeLabel(node, id)}." else "No pude activar ${nodeLabel(node, id)} sin usar coordenadas.",
        )
    }

    private fun longClickMappedNode(id: String, node: AccessibilityNodeInfo): LeoUiActionResult {
        if (node.isPassword || isHighRiskNode(node)) return blockedSensitive()
        if (!node.isEnabled || !node.isLongClickable) return LeoUiActionResult(false, "$id no admite una pulsación larga segura.")
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        return LeoUiActionResult(ok, if (ok) "Mantuve presionado ${nodeLabel(node, id)}." else "No pude mantener presionado $id.")
    }

    private fun typeIntoNode(id: String, node: AccessibilityNodeInfo, value: String): LeoUiActionResult {
        if (!node.isEditable) return LeoUiActionResult(false, "$id no es un campo editable.")
        if (node.isPassword || isHighRiskNode(node)) return blockedSensitive("LEO no escribe datos sensibles mediante automatización.")
        if (value.length > MAX_TYPE_CHARS || NikoUiTaskPolicy.isSensitiveControl(value)) {
            return blockedSensitive("Bloqueé texto que podría contener una acción o dato sensible.")
        }
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        return LeoUiActionResult(ok, if (ok) "Escribí en ${nodeLabel(node, id)}." else "No pude escribir en $id.")
    }

    private fun selectMappedNode(id: String, node: AccessibilityNodeInfo): LeoUiActionResult {
        if (node.isPassword || isHighRiskNode(node)) return blockedSensitive()
        if (!node.isEnabled) return LeoUiActionResult(false, "El control $id está deshabilitado.")
        if (node.isSelected) return LeoUiActionResult(true, "${nodeLabel(node, id)} ya estaba seleccionado.")
        if (node.actionList.none { it.id == AccessibilityNodeInfo.ACTION_SELECT }) {
            return LeoUiActionResult(false, "$id no expone una selección segura a Accesibilidad.")
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        return LeoUiActionResult(ok, if (ok) "Seleccioné ${nodeLabel(node, id)}." else "No pude seleccionar $id.")
    }

    private fun toggleMappedNode(id: String, node: AccessibilityNodeInfo, desired: Boolean?): LeoUiActionResult {
        if (desired == null) return LeoUiActionResult(false, "Falta el estado esperado para $id.")
        if (node.isPassword || isHighRiskNode(node)) return blockedSensitive()
        if (!node.isEnabled || !node.isCheckable) return LeoUiActionResult(false, "$id no es un interruptor seguro.")
        if (node.isChecked == desired) {
            return LeoUiActionResult(true, "${nodeLabel(node, id)} ya estaba ${if (desired) "activado" else "desactivado"}.")
        }
        if (!node.isClickable) return LeoUiActionResult(false, "$id no permite cambiar su estado mediante Accesibilidad.")
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return LeoUiActionResult(
            ok,
            if (ok) "Cambié ${nodeLabel(node, id)}; verificaré el estado en el próximo snapshot." else "No pude cambiar $id.",
        )
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
            try {
                if (scrollNodeForward(child)) return true
            } finally {
                runCatching { child.recycle() }
            }
        }
        return false
    }

    private fun scrollNodeBackward(node: AccessibilityNodeInfo): Boolean {
        if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (scrollNodeBackward(child)) return true
            } finally {
                runCatching { child.recycle() }
            }
        }
        return false
    }

    private fun isHighRiskNode(node: AccessibilityNodeInfo): Boolean {
        val value = listOf(node.text, node.contentDescription, node.viewIdResourceName)
            .joinToString(" ") { it?.toString().orEmpty() }
        return NikoUiTaskPolicy.isSensitiveControl(value)
    }

    private fun nodeLabel(node: AccessibilityNodeInfo, fallback: String): String =
        node.text?.toString()?.takeIf(String::isNotBlank)
            ?: node.contentDescription?.toString()?.takeIf(String::isNotBlank)
            ?: node.viewIdResourceName?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: fallback

    private fun blockedSensitive(message: String = "Bloqueé un control sensible. Esa acción necesita interacción directa del usuario.") =
        LeoUiActionResult(false, message, blocked = true)

    private fun isSnapshotCurrent(snapshotId: Long, revision: Long): Boolean =
        snapshotId > 0L &&
            snapshotId == activeSnapshotId &&
            revision == activeSnapshotRevision &&
            revision == uiRevision.get()

    private fun invalidateSnapshot() {
        uiRevision.incrementAndGet()
        activeSnapshotId = 0L
        activeSnapshotRevision = -1L
        clearNodeMap()
    }

    private fun stableSignature(pkg: String, tree: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest("$pkg\n$tree".toByteArray(Charsets.UTF_8))
        return bytes.take(10).joinToString("") { "%02x".format(it) }
    }

    private fun escape(value: String): String = value.replace('"', '\'').replace('|', ' ')

    private inline fun withRoot(block: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val success = block(root)
            if (success) uiRevision.incrementAndGet()
            success
        } finally {
            runCatching { root.recycle() }
        }
    }

    private fun clearNodeMap() {
        nodeMap.values.forEach { runCatching { it.recycle() } }
        nodeMap.clear()
        nodeCounter.set(0)
        activeSnapshotId = 0L
        activeSnapshotRevision = -1L
    }

    companion object {
        private const val MAX_TREE_CHARS = 12_000
        private const val MAX_TYPE_CHARS = 1_500
        private val REVISION_EVENTS = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_SELECTED,
        )

        @Volatile
        var instance: NikoAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }
}
