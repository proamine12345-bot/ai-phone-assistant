package com.aiphoneagent.core

/**
 * Safety layer. The agent never bypasses Android permissions; this only adds
 * app-level guards on top of them.
 */
object SafetyGuard {

    /** Packages the agent must never touch. */
    private val blockedPackages = setOf(
        "com.android.settings.accessibility",
        "com.android.vending.billing",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    private val sensitiveKeywords = listOf(
        "حذف", "امسح", "ادفع", "دفع", "شراء", "تحويل", "كلمة المرور", "كلمة السر", "رمز التحقق",
        "delete", "uninstall", "pay", "purchase", "transfer", "password", "otp", "verification code",
        "disable", "تعطيل", "root", "device admin", "بصمة",
    )

    fun isPackageAllowed(pkg: String?, allowed: Set<String>): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (blockedPackages.any { pkg.startsWith(it) }) return false
        if (allowed.isEmpty()) return false
        return allowed.contains(pkg)
    }

    fun needsConfirmation(step: AgentStep): Boolean {
        if (step.dangerous) return true
        val haystack = listOfNotNull(step.description, step.text, step.target)
            .joinToString(" ")
            .lowercase()
        return sensitiveKeywords.any { haystack.contains(it.lowercase()) }
    }

    /** Reject plans that try to disable protections or hide activity from the user. */
    fun rejectionReason(plan: AgentPlan): String? {
        val text = (plan.goal + " " + plan.summary + " " + plan.steps.joinToString(" ") { it.description })
            .lowercase()
        val forbidden = listOf("تعطيل الحماية", "تجاوز الصلاحيات", "disable security", "bypass permission", "hide from user", "إخفاء الإجراءات")
        return forbidden.firstOrNull { text.contains(it) }?.let { "طلب مرفوض لأسباب أمنية: $it" }
    }
}
