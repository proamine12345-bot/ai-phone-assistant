package com.aiphoneagent.agent

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.aiphoneagent.core.AgentBus
import com.aiphoneagent.core.AgentStep
import com.aiphoneagent.service.AgentAccessibilityService
import kotlinx.coroutines.delay

/** Result of a single action attempt. */
data class ActionResult(val ok: Boolean, val detail: String = "", val askUser: String? = null)

/**
 * Maps a protocol action to real Android interaction. Generic by design:
 * no app-specific branching anywhere.
 */
object ActionExecutor {

    suspend fun execute(
        service: AgentAccessibilityService,
        step: AgentStep,
        allowedPackages: Set<String>,
    ): ActionResult {
        return when (step.action) {
            "open_app" -> openApp(service, step, allowedPackages)

            "tap_text", "tap_id" -> {
                val query = step.target ?: step.text
                    ?: return ActionResult(false, "لا يوجد هدف للنقر")
                val node = ScreenReader.findNode(service, query)
                    ?: return ActionResult(false, "لم يتم العثور على «$query» على الشاشة")
                val ok = GestureController.tapNode(service, node)
                ActionResult(ok, if (ok) "نقر على «$query»" else "فشل النقر على «$query»")
            }

            "long_press_text" -> {
                val query = step.target ?: return ActionResult(false, "لا يوجد هدف")
                val node = ScreenReader.findNode(service, query)
                    ?: return ActionResult(false, "لم يتم العثور على «$query»")
                val ok = GestureController.longPressNode(service, node)
                ActionResult(ok, "ضغط مطوّل على «$query»")
            }

            "type_text" -> typeText(service, step)

            "clear_text" -> {
                val field = ScreenReader.editableNode(service, step.target)
                    ?: return ActionResult(false, "لا يوجد حقل نص")
                val ok = setText(field, "")
                ActionResult(ok, "تم تفريغ حقل النص")
            }

            "press_back" -> ActionResult(service.globalBack(), "زر الرجوع")
            "press_home" -> ActionResult(service.globalHome(), "زر الرئيسية")
            "open_notifications" -> ActionResult(service.globalNotifications(), "لوحة الإشعارات")

            "press_enter" -> {
                val field = ScreenReader.editableNode(service, step.target)
                if (field != null && field.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    // Most keyboards send the action via IME; fall back to a scroll-free swipe-less enter
                }
                val ok = field?.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION) ?: false
                // Best effort: many apps expose a "search"/"send" button instead.
                val alt = listOf("بحث", "search", "إرسال", "send", "Go", "تم", "done")
                    .asSequence()
                    .mapNotNull { ScreenReader.findNode(service, it) }
                    .firstOrNull()
                if (alt != null) {
                    val tapped = GestureController.tapNode(service, alt)
                    return ActionResult(tapped, "تأكيد الإدخال")
                }
                ActionResult(ok, "تأكيد الإدخال")
            }

            "scroll" -> {
                val ok = GestureController.scroll(AgentServiceHandle(service), step.direction ?: "down")
                ActionResult(ok, "تمرير ${step.direction ?: "down"}")
            }

            "swipe" -> {
                val ok = GestureController.swipe(service, step.direction ?: "up", step.durationMs ?: 300)
                ActionResult(ok, "سحب ${step.direction ?: "up"}")
            }

            "tap_point" -> {
                val x = step.x ?: return ActionResult(false, "إحداثيات ناقصة")
                val y = step.y ?: return ActionResult(false, "إحداثيات ناقصة")
                val ok = GestureController.tapNormalized(service, x, y)
                ActionResult(ok, "نقر عند ($x, $y)")
            }

            "wait" -> {
                delay((step.durationMs ?: 1000).coerceIn(100, 60_000))
                ActionResult(true, "انتظار")
            }

            "assert_text" -> {
                val query = step.verify ?: step.target ?: return ActionResult(true, "لا شيء للتحقق")
                val found = ScreenReader.containsText(service, query)
                ActionResult(found, if (found) "تم التحقق من «$query»" else "لم يظهر «$query»")
            }

            "read_screen" -> ActionResult(true, "قراءة الشاشة")

            "launch_url" -> {
                val url = step.target ?: step.text ?: return ActionResult(false, "لا يوجد رابط")
                ActionResult(AppLauncher.openUrl(service, url), "فتح رابط")
            }

            "ask_user" -> ActionResult(
                ok = false,
                detail = "بحاجة إلى تأكيد المستخدم",
                askUser = step.description.ifBlank { step.text ?: "هل أتابع؟" },
            )

            "finish" -> ActionResult(true, "انتهت المهمة")

            else -> ActionResult(false, "إجراء غير مدعوم: ${step.action}")
        }
    }

    private fun openApp(
        service: AgentAccessibilityService,
        step: AgentStep,
        allowed: Set<String>,
    ): ActionResult {
        val pkg = step.appPackage?.takeIf { it.isNotBlank() }
            ?: AppLauncher.resolvePackage(service, step.target)
            ?: return ActionResult(false, "لم يتم التعرف على التطبيق «${step.target}»")

        if (!allowed.contains(pkg)) {
            return ActionResult(
                ok = false,
                detail = "التطبيق $pkg غير مسموح به",
                askUser = "يحتاج الوكيل إذنك للتحكم بالتطبيق ($pkg). هل تسمح؟",
            )
        }
        val ok = AppLauncher.launch(service, pkg)
        AgentBus.log(if (ok) "فتح التطبيق" else "فشل فتح التطبيق", pkg, ok)
        return ActionResult(ok, "فتح $pkg")
    }

    private suspend fun typeText(service: AgentAccessibilityService, step: AgentStep): ActionResult {
        val value = step.text ?: return ActionResult(false, "لا يوجد نص للكتابة")
        var field = ScreenReader.editableNode(service, step.target)
        if (field == null && step.target != null) {
            ScreenReader.findNode(service, step.target)?.let { GestureController.tapNode(service, it) }
            delay(400)
            field = ScreenReader.editableNode(service, step.target)
        }
        if (field == null) return ActionResult(false, "لا يوجد حقل نص قابل للكتابة")
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val ok = setText(field, value)
        return ActionResult(ok, if (ok) "كتابة نص" else "فشل إدخال النص")
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }
}
