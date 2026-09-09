package com.aiphoneagent.agent

import android.content.Context
import android.content.Intent
import android.net.Uri

/** App launching via Intents — the safe, permission-respecting path. */
object AppLauncher {

    fun resolvePackage(context: Context, target: String?): String? {
        if (target.isNullOrBlank()) return null
        val pm = context.packageManager
        if (target.contains(".") && runCatching { pm.getPackageInfo(target, 0) }.isSuccess) return target

        val needle = target.trim().lowercase()
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .firstOrNull { pm.getApplicationLabel(it).toString().lowercase().contains(needle) }
            ?.packageName
    }

    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackageCompat(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        return true
    }

    fun openUrl(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openAccessibilitySettings(context: Context) {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun android.content.pm.PackageManager.getLaunchIntentForPackageCompat(pkg: String): Intent? =
        getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(pkg)
            .let { intent -> queryIntentActivities(intent, 0).firstOrNull()?.let { intent } }
}
