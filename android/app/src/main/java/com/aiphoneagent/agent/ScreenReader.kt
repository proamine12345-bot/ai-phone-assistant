package com.aiphoneagent.agent

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.aiphoneagent.core.ScreenNode
import com.aiphoneagent.core.ScreenSnapshot
import com.aiphoneagent.service.AgentAccessibilityService
import com.aiphoneagent.vision.OcrEngine

/**
 * Vision layer (structured half): turns the live accessibility tree into a
 * compact snapshot the AI can reason about. OCR is appended when the tree is
 * too poor (games, canvas/WebGL UIs).
 */
object ScreenReader {

    private const val MAX_NODES = 220

    fun snapshot(service: AgentAccessibilityService, withOcr: Boolean = true): ScreenSnapshot {
        val nodes = mutableListOf<ScreenNode>()
        val root = service.root()
        if (root != null) collect(root, nodes, 0)

        val poorTree = nodes.count { !it.text.isNullOrBlank() || !it.desc.isNullOrBlank() } < 3
        val ocr = if (withOcr && poorTree) OcrEngine.lastText() else null

        return ScreenSnapshot(
            packageName = root?.packageName?.toString() ?: service.lastPackage,
            activity = service.lastActivity,
            nodes = nodes,
            ocrText = ocr,
        )
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<ScreenNode>, depth: Int) {
        if (out.size >= MAX_NODES || depth > 40) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val interesting = !text.isNullOrBlank() || !desc.isNullOrBlank() ||
            node.isClickable || node.isEditable || node.isScrollable

        if (interesting) {
            val rect = Rect().also { node.getBoundsInScreen(it) }
            out += ScreenNode(
                text = text?.take(300),
                desc = desc?.take(300),
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                clickable = node.isClickable,
                editable = node.isEditable,
                scrollable = node.isScrollable,
                bounds = "${rect.left},${rect.top},${rect.right},${rect.bottom}",
            )
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, out, depth + 1) }
        }
    }

    /** Find the best node matching a text/description/viewId query. */
    fun findNode(service: AgentAccessibilityService, query: String): AccessibilityNodeInfo? {
        val root = service.root() ?: return null
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return null

        // 1) exact accessibility text search
        root.findAccessibilityNodeInfosByText(query)
            ?.firstOrNull { it.isVisibleToUser }
            ?.let { return it }

        // 2) viewId search
        runCatching { root.findAccessibilityNodeInfosByViewId(query) }
            .getOrNull()?.firstOrNull { it.isVisibleToUser }?.let { return it }

        // 3) manual fuzzy walk over text / description / viewId
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        walk(root) { candidates += it }
        return candidates
            .asSequence()
            .filter { it.isVisibleToUser }
            .map { node ->
                val hay = listOfNotNull(
                    node.text?.toString(),
                    node.contentDescription?.toString(),
                    node.viewIdResourceName,
                ).joinToString(" ").lowercase()
                node to score(hay, needle)
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun score(hay: String, needle: String): Int = when {
        hay.isBlank() -> 0
        hay == needle -> 100
        hay.contains(needle) -> 70
        needle.split(" ").any { it.length > 2 && hay.contains(it) } -> 40
        else -> 0
    }

    private fun walk(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, visit) }
    }

    /** Nearest ancestor (or self) that can actually handle a click. */
    fun clickableOf(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        var hops = 0
        while (current != null && hops < 8) {
            if (current.isClickable) return current
            current = current.parent
            hops++
        }
        return null
    }

    fun editableNode(service: AgentAccessibilityService, hint: String?): AccessibilityNodeInfo? {
        val root = service.root() ?: return null
        val all = mutableListOf<AccessibilityNodeInfo>()
        walk(root) { if (it.isEditable && it.isVisibleToUser) all += it }
        if (all.isEmpty()) return null
        if (hint.isNullOrBlank()) return all.firstOrNull { it.isFocused } ?: all.first()
        val needle = hint.lowercase()
        return all.firstOrNull { node ->
            listOfNotNull(node.text?.toString(), node.contentDescription?.toString(), node.viewIdResourceName)
                .joinToString(" ").lowercase().contains(needle)
        } ?: all.firstOrNull { it.isFocused } ?: all.first()
    }

    fun containsText(service: AgentAccessibilityService, query: String): Boolean {
        val snap = snapshot(service, withOcr = true)
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return true
        val inNodes = snap.nodes.any {
            (it.text?.lowercase()?.contains(needle) == true) || (it.desc?.lowercase()?.contains(needle) == true)
        }
        return inNodes || (snap.ocrText?.lowercase()?.contains(needle) == true)
    }
}
