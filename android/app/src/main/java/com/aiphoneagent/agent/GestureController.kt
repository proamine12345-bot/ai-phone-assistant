package com.aiphoneagent.agent

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Touch layer: taps, long presses, swipes and scrolls. Works for regular apps
 * and for games/canvas UIs where nodes are unavailable (normalized coordinates).
 */
object GestureController {

    suspend fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val handled = ScreenReader.clickableOf(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        if (handled) return true
        val rect = Rect().also { node.getBoundsInScreen(it) }
        if (rect.isEmpty) return false
        return tapAt(service, rect.exactCenterX(), rect.exactCenterY())
    }

    suspend fun longPressNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val handled = ScreenReader.clickableOf(node)
            ?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) ?: false
        if (handled) return true
        val rect = Rect().also { node.getBoundsInScreen(it) }
        return tapAt(service, rect.exactCenterX(), rect.exactCenterY(), durationMs = 600)
    }

    suspend fun tapAt(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long = 60,
    ): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(service, path, 0, durationMs)
    }

    /** Normalized tap (0..1) — resolution independent fallback. */
    suspend fun tapNormalized(service: AccessibilityService, nx: Float, ny: Float): Boolean {
        val metrics = service.resources.displayMetrics
        return tapAt(service, nx * metrics.widthPixels, ny * metrics.heightPixels)
    }

    suspend fun swipe(
        service: AccessibilityService,
        direction: String,
        durationMs: Long = 300,
    ): Boolean {
        val m = service.resources.displayMetrics
        val w = m.widthPixels.toFloat()
        val h = m.heightPixels.toFloat()
        val path = Path()
        when (direction) {
            "up" -> { path.moveTo(w * 0.5f, h * 0.75f); path.lineTo(w * 0.5f, h * 0.25f) }
            "down" -> { path.moveTo(w * 0.5f, h * 0.3f); path.lineTo(w * 0.5f, h * 0.8f) }
            "left" -> { path.moveTo(w * 0.8f, h * 0.5f); path.lineTo(w * 0.2f, h * 0.5f) }
            else -> { path.moveTo(w * 0.2f, h * 0.5f); path.lineTo(w * 0.8f, h * 0.5f) }
        }
        return dispatch(service, path, 0, durationMs)
    }

    /** Prefer a real scrollable node; fall back to a swipe gesture. */
    suspend fun scroll(
        service: AgentServiceHandle,
        direction: String,
    ): Boolean {
        val svc = service.service
        val root = svc.rootInActiveWindow
        if (root != null) {
            val scrollable = firstScrollable(root)
            if (scrollable != null) {
                val action = if (direction == "up" || direction == "left") {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                }
                if (scrollable.performAction(action)) return true
            }
        }
        return swipe(svc, if (direction == "down") "up" else if (direction == "up") "down" else direction)
    }

    private fun firstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child -> firstScrollable(child)?.let { return it } }
        }
        return null
    }

    private suspend fun dispatch(
        service: AccessibilityService,
        path: Path,
        startTime: Long,
        durationMs: Long,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val stroke = GestureDescription.StrokeDescription(path, startTime, durationMs.coerceAtLeast(20))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(description: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(description: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            },
            null,
        )
        if (!ok && cont.isActive) cont.resume(false)
    }
}

/** Thin wrapper so gesture helpers can accept the concrete agent service. */
class AgentServiceHandle(val service: AccessibilityService)
