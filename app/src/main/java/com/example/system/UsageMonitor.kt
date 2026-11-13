package com.example.system

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AppSessionStat(
    val packageName: String,
    val appName: String,
    val category: String,
    val foregroundMinutes: Long,
    val launchCount: Int,
    val lastUsedMs: Long,
    val hourlyMinutes: Map<Int, Long>,
    val productivityWeight: Float
)

data class UsageWindowSnapshot(
    val usageAccessGranted: Boolean,
    val dateKey: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val appStats: List<AppSessionStat>,
    val hourlyTotalMinutes: Map<Int, Long>,
    val screenSessionCount: Int
)

/**
 * Reads Android foreground app usage and launch-frequency telemetry strictly through
 * permitted UsageStatsManager APIs (no keylogging or private in-app monitoring).
 */
class UsageMonitor(private val context: Context) {

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun collectDaySnapshot(dayOffset: Int = 0): UsageWindowSnapshot {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startMs = cal.timeInMillis
        val endMs = if (dayOffset == 0) {
            System.currentTimeMillis()
        } else {
            cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis - 1L
        }
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(startMs))

        if (!hasUsageStatsPermission()) {
            return UsageWindowSnapshot(
                usageAccessGranted = false,
                dateKey = dateKey,
                windowStartMs = startMs,
                windowEndMs = endMs,
                appStats = emptyList(),
                hourlyTotalMinutes = emptyMap(),
                screenSessionCount = 0
            )
        }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return UsageWindowSnapshot(false, dateKey, startMs, endMs, emptyList(), emptyMap(), 0)

        val pm = context.packageManager
        val durationMsMap = mutableMapOf<String, Long>()
        val lastUsedMap = mutableMapOf<String, Long>()
        val launchMap = mutableMapOf<String, Int>()
        val hourlyAppMs = mutableMapOf<String, MutableMap<Int, Long>>()
        val hourlyTotalMs = mutableMapOf<Int, Long>()
        var screenSessions = 0

        try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startMs, endMs)
            stats?.forEach { stat ->
                if (stat.packageName != context.packageName && stat.totalTimeInForeground > 0L) {
                    durationMsMap[stat.packageName] =
                        (durationMsMap[stat.packageName] ?: 0L) + stat.totalTimeInForeground
                    val prevLast = lastUsedMap[stat.packageName] ?: 0L
                    if (stat.lastTimeUsed > prevLast) lastUsedMap[stat.packageName] = stat.lastTimeUsed
                }
            }
        } catch (_: Exception) {
        }

        try {
            val events = usm.queryEvents(startMs, endMs)
            val event = UsageEvents.Event()
            val activeStarts = mutableMapOf<String, Long>()
            val eventCal = Calendar.getInstance()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                if (pkg == context.packageName) continue

                when (event.eventType) {
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        screenSessions += 1
                    }
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        launchMap[pkg] = (launchMap[pkg] ?: 0) + 1
                        activeStarts[pkg] = event.timeStamp
                        lastUsedMap[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        val startedAt = activeStarts.remove(pkg)
                        if (startedAt != null && event.timeStamp > startedAt) {
                            val delta = (event.timeStamp - startedAt).coerceAtMost(4 * 3_600_000L)
                            eventCal.timeInMillis = startedAt
                            val hour = eventCal.get(Calendar.HOUR_OF_DAY)
                            val perAppHour = hourlyAppMs.getOrPut(pkg) { mutableMapOf() }
                            perAppHour[hour] = (perAppHour[hour] ?: 0L) + delta
                            hourlyTotalMs[hour] = (hourlyTotalMs[hour] ?: 0L) + delta
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val allPackages = (durationMsMap.keys + launchMap.keys).distinct()
        val appStats = allPackages.mapNotNull { pkg ->
            val fgMins = ((durationMsMap[pkg] ?: 0L) / 60_000L).coerceAtLeast(0L)
            val launches = launchMap[pkg] ?: 0
            val hourMins = hourlyAppMs[pkg].orEmpty()
                .mapValues { (_, ms) -> (ms / 60_000L).coerceAtLeast(1L) }
            val resolvedMinutes = if (fgMins > 0L) fgMins else hourMins.values.sum()
            if (resolvedMinutes <= 0L && launches == 0) return@mapNotNull null

            val appName = resolveAppLabel(pm, pkg)
            val category = classifyCategory(pkg, appName)
            AppSessionStat(
                packageName = pkg,
                appName = appName,
                category = category,
                foregroundMinutes = resolvedMinutes,
                launchCount = launches.coerceAtLeast(if (resolvedMinutes > 0) 1 else 0),
                lastUsedMs = lastUsedMap[pkg] ?: startMs,
                hourlyMinutes = hourMins,
                productivityWeight = weightForCategory(category)
            )
        }.sortedByDescending { it.foregroundMinutes }

        val hourlyTotals = hourlyTotalMs.mapValues { (_, ms) -> (ms / 60_000L).coerceAtLeast(0L) }
        return UsageWindowSnapshot(
            usageAccessGranted = true,
            dateKey = dateKey,
            windowStartMs = startMs,
            windowEndMs = endMs,
            appStats = appStats,
            hourlyTotalMinutes = hourlyTotals,
            screenSessionCount = screenSessions.coerceAtLeast(appStats.sumOf { it.launchCount } / 2)
        )
    }

    private fun resolveAppLabel(pm: PackageManager, pkg: String): String {
        return try {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString().ifBlank { pkg }
        } catch (_: Exception) {
            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    companion object {
        fun classifyCategory(pkg: String, label: String): String {
            val text = "$pkg $label".lowercase(Locale.US)
            return when {
                text.contains("tiktok") || text.contains("musically") ||
                    text.contains("instagram") || text.contains("facebook") ||
                    text.contains("twitter") || text.contains("reddit") ||
                    text.contains("snapchat") || text.contains("threads") -> "social"
                text.contains("youtube") || text.contains("netflix") ||
                    text.contains("twitch") || text.contains("primevideo") ||
                    text.contains("disney") -> "video"
                text.contains("whatsapp") || text.contains("telegram") ||
                    text.contains("slack") || text.contains("discord") ||
                    text.contains("messenger") || text.contains("gmail") -> "communication"
                text.contains("docs") || text.contains("notion") ||
                    text.contains("office") || text.contains("sheets") ||
                    text.contains("calendar") || text.contains("notes") ||
                    text.contains("github") || text.contains("studio") ||
                    text.contains("keep") || text.contains("drive") -> "productive"
                text.contains("chrome") || text.contains("browser") ||
                    text.contains("firefox") || text.contains("opera") -> "browser"
                text.contains("game") || text.contains("pubg") ||
                    text.contains("roblox") || text.contains("chess") -> "gaming"
                else -> "other"
            }
        }

        fun weightForCategory(category: String): Float = when (category) {
            "productive", "focus" -> 0.9f
            "communication" -> 0.1f
            "browser", "other" -> 0.0f
            "gaming", "video" -> -0.65f
            "social" -> -0.85f
            else -> 0.0f
        }
    }
}
