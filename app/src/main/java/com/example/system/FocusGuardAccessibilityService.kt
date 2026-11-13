package com.example.system

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Calendar

class FocusGuardAccessibilityService : AccessibilityService() {

    private lateinit var guardPrefs: FocusGuardPreferences
    private var lastTriggerMs: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        guardPrefs = FocusGuardPreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!::guardPrefs.isInitialized) {
            guardPrefs = FocusGuardPreferences(this)
        }

        val pkg = event.packageName?.toString() ?: return
        guardPrefs.recordForegroundPackage(pkg)
        if (pkg == packageName || pkg == "com.android.systemui" || pkg.contains("launcher")) {
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastTriggerMs < 1500L) return

        // 1. Check app limits / sleep lockout / deep work rules
        val liveMins = queryTodayMinutesForPackage(pkg)
        val pkgResult = guardPrefs.evaluatePackage(pkg, liveMins)
        if (pkgResult.shouldBlock) {
            enforceBlock(pkgResult)
            return
        }

        // 2. Inspect browser URL bars for blocked distracting domains
        if (isBrowserPackage(pkg)) {
            val root = rootInActiveWindow ?: return
            val urlText = extractBrowserUrl(root)
            if (!urlText.isNullOrBlank()) {
                val urlResult = guardPrefs.evaluateUrlText(urlText)
                if (urlResult.shouldBlock) {
                    enforceBlock(urlResult)
                }
            }
        }
    }

    private fun enforceBlock(result: GuardEvalResult) {
        lastTriggerMs = System.currentTimeMillis()
        performGlobalAction(GLOBAL_ACTION_HOME)
        val lockIntent = Intent(this, FocusLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(FocusLockActivity.EXTRA_TITLE, result.reasonTitle)
            putExtra(FocusLockActivity.EXTRA_DETAIL, result.reasonDetail)
            putExtra(FocusLockActivity.EXTRA_UNLOCK_AT, result.unlockTimeLabel)
        }
        startActivity(lockIntent)
    }

    private fun queryTodayMinutesForPackage(pkg: String): Long? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                cal.timeInMillis,
                System.currentTimeMillis()
            )
            stats?.filter { it.packageName == pkg }?.sumOf { it.totalTimeInForeground / 60_000L }
        } catch (_: Exception) {
            null
        }
    }

    private fun isBrowserPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("chrome") || lower.contains("browser") ||
            lower.contains("firefox") || lower.contains("opera") ||
            lower.contains("edge") || lower.contains("brave")
    }

    private fun extractBrowserUrl(root: AccessibilityNodeInfo): String? {
        val knownUrlIds = listOf(
            "com.android.chrome:id/url_bar",
            "org.mozilla.firefox:id/url_bar_title",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.brave.browser:id/url_bar",
            "com.microsoft.emmx:id/url_bar"
        )
        for (id in knownUrlIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            val text = nodes?.firstOrNull()?.text?.toString()
            if (!text.isNullOrBlank()) return text
        }
        return null
    }

    override fun onInterrupt() {
        // No-op
    }
}
