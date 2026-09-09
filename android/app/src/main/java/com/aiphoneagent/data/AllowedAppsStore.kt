package com.aiphoneagent.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class InstalledApp(val label: String, val packageName: String, val allowed: Boolean)

/** User consent per app: the agent only acts inside apps listed here. */
class AllowedAppsStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)

    private val _allowed = MutableStateFlow(load())
    val allowed: StateFlow<Set<String>> = _allowed

    private fun load(): Set<String> = prefs.getStringSet(KEY, emptySet())?.toSet() ?: emptySet()

    fun toggle(packageName: String) {
        val next = _allowed.value.toMutableSet()
        if (!next.remove(packageName)) next.add(packageName)
        prefs.edit().putStringSet(KEY, next).apply()
        _allowed.value = next
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
        _allowed.value = emptySet()
    }

    fun installedApps(): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { info: ApplicationInfo ->
                InstalledApp(
                    label = pm.getApplicationLabel(info).toString(),
                    packageName = info.packageName,
                    allowed = _allowed.value.contains(info.packageName),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private companion object { const val KEY = "allowed_packages" }
}
