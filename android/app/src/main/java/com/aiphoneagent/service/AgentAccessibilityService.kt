package com.aiphoneagent.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.aiphoneagent.core.AgentBus

/**
 * Android Agent entry point. Enabled only after the user grants Accessibility
 * access in system settings. Provides screen reading + gesture dispatch to the
 * Task Engine; it never runs anything on its own.
 */
class AgentAccessibilityService : AccessibilityService() {

    @Volatile var lastPackage: String? = null
        private set
    @Volatile var lastActivity: String? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        AgentBus.log("خدمة الوصول متصلة")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastPackage = e.packageName?.toString()
            lastActivity = e.className?.toString()
        }
    }

    override fun onInterrupt() {
        AgentBus.log("تمت مقاطعة خدمة الوصول", ok = false)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        AgentBus.emergencyStop()
        return super.onUnbind(intent)
    }

    fun root(): AccessibilityNodeInfo? = rootInActiveWindow

    fun globalBack() = performGlobalAction(GLOBAL_ACTION_BACK)
    fun globalHome() = performGlobalAction(GLOBAL_ACTION_HOME)
    fun globalNotifications() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    companion object {
        @Volatile
        var instance: AgentAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
