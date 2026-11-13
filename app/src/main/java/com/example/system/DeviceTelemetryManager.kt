package com.example.system

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.roundToInt

data class BatteryTelemetry(
    val levelPercent: Int = 0,
    val isCharging: Boolean = false,
    val chargeSource: String = "Battery",
    val temperatureC: Float = 0f,
    val voltageMv: Int = 0,
    val healthLabel: String = "Good",
    val currentMa: Int = 0,
    val powerSaveMode: Boolean = false
)

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val minutesToday: Long,
    val categoryGuess: String,
    val isRunningBackground: Boolean,
    val standbyBucketLabel: String
)

data class SleepCircadianInsights(
    val estimatedSleepOnsetLabel: String,
    val estimatedSleepHours: Double,
    val recommendedWakeWindows: List<String>,
    val stepCountToday: Int,
    val healthConnectAvailable: Boolean
)

data class SystemPermissionsState(
    val usageStatsGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val dndPolicyGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val exactAlarmGranted: Boolean = false,
    val microphoneGranted: Boolean = false
)

class DeviceTelemetryManager(private val context: Context) {

    fun checkPermissions(): SystemPermissionsState {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val usageMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        val usageGranted = usageMode == AppOpsManager.MODE_ALLOWED

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val accessibilityGranted = enabledServices.any {
            it.resolveInfo?.serviceInfo?.packageName == context.packageName
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val dndGranted = nm.isNotificationPolicyAccessGranted

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val exactAlarmGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmMgr.canScheduleExactAlarms()
        } else {
            true
        }

        val micGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        return SystemPermissionsState(
            usageStatsGranted = usageGranted,
            accessibilityEnabled = accessibilityGranted,
            dndPolicyGranted = dndGranted,
            notificationsGranted = notifGranted,
            exactAlarmGranted = exactAlarmGranted,
            microphoneGranted = micGranted
        )
    }

    fun readBatteryTelemetry(): BatteryTelemetry {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) ((level * 100f) / scale).roundToInt() else 0

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val source = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Adapter"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "On Battery"
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD)
            ?: BatteryManager.BATTERY_HEALTH_GOOD
        val healthLabel = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Degraded"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Nominal"
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentMicro = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentMicro != Int.MIN_VALUE) currentMicro / 1000 else 0

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return BatteryTelemetry(
            levelPercent = pct,
            isCharging = isCharging,
            chargeSource = source,
            temperatureC = tempC,
            voltageMv = voltage,
            healthLabel = healthLabel,
            currentMa = currentMa,
            powerSaveMode = pm.isPowerSaveMode
        )
    }

    fun queryAppsAndUsage(): List<AppUsageInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningPackages = am.runningAppProcesses
            ?.flatMap { it.pkgList?.toList().orEmpty() }
            ?.toSet()
            .orEmpty()

        val usageMap = mutableMapOf<String, Long>()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        if (checkPermissions().usageStatsGranted) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val end = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            stats?.forEach { stat ->
                val mins = stat.totalTimeInForeground / 60_000L
                if (mins > 0) {
                    usageMap[stat.packageName] = (usageMap[stat.packageName] ?: 0L) + mins
                }
            }
        }

        val seen = HashSet<String>()
        val result = mutableListOf<AppUsageInfo>()
        for (ri in resolveInfos) {
            val pkg = ri.activityInfo?.packageName ?: continue
            if (pkg == context.packageName || !seen.add(pkg)) continue
            val appInfo = ri.activityInfo.applicationInfo
            val isSystemOnly = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            val label = ri.loadLabel(pm).toString()
            val mins = usageMap[pkg] ?: 0L
            val isRunning = runningPackages.contains(pkg)
            if (isSystemOnly && mins == 0L && !isKnownSocialOrBrowser(pkg)) continue

            val bucket = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    when (usm.appStandbyBucket) {
                        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "Active"
                        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "Working"
                        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "Frequent"
                        UsageStatsManager.STANDBY_BUCKET_RARE -> "Rare"
                        else -> "Restricted"
                    }
                } catch (_: Exception) {
                    "Active"
                }
            } else {
                "Active"
            }

            result.add(
                AppUsageInfo(
                    packageName = pkg,
                    appName = label,
                    minutesToday = mins,
                    categoryGuess = guessCategory(pkg, label),
                    isRunningBackground = isRunning,
                    standbyBucketLabel = bucket
                )
            )
        }
        return result.sortedWith(
            compareByDescending<AppUsageInfo> { it.minutesToday }
                .thenByDescending { it.isRunningBackground }
                .thenBy { it.appName }
        )
    }

    fun killBackgroundApp(packageName: String): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openAppDetailsSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun setDeepWorkDnd(enable: Boolean): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return false
        nm.setInterruptionFilter(
            if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        return true
    }

    fun computeSleepCircadianInsights(
        bedtimeHour: Int,
        bedtimeMinute: Int,
        wakeHour: Int,
        wakeMinute: Int,
        stepCount: Int
    ): SleepCircadianInsights {
        // Compute 90-minute REM sleep cycles from bedtime & activity recovery adjustment
        val bedCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, bedtimeHour)
            set(Calendar.MINUTE, bedtimeMinute)
            set(Calendar.SECOND, 0)
        }
        // Account for 15 min average sleep onset latency
        val sleepStartMs = bedCal.timeInMillis + 15 * 60_000L
        val cycleMs = 90 * 60_000L
        val cycles = listOf(4, 5, 6) // 6h, 7.5h, 9h
        val windows = cycles.map { count ->
            val wakeCal = Calendar.getInstance().apply {
                timeInMillis = sleepStartMs + count * cycleMs
            }
            val h = wakeCal.get(Calendar.HOUR_OF_DAY)
            val m = wakeCal.get(Calendar.MINUTE)
            val hrs = count * 1.5
            String.format("%02d:%02d (%.1fh / %d cycles)", h, m, hrs, count)
        }

        val bedTotalMin = bedtimeHour * 60 + bedtimeMinute
        val wakeTotalMin = wakeHour * 60 + wakeMinute
        val diffMin = if (wakeTotalMin >= bedTotalMin) {
            wakeTotalMin - bedTotalMin
        } else {
            (24 * 60 - bedTotalMin) + wakeTotalMin
        }
        val estimatedHours = (diffMin / 60.0).coerceIn(1.0, 14.0)

        val hcIntent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
        val hcAvailable = context.packageManager.resolveActivity(hcIntent, 0) != null

        return SleepCircadianInsights(
            estimatedSleepOnsetLabel = String.format("%02d:%02d", bedtimeHour, bedtimeMinute),
            estimatedSleepHours = estimatedHours,
            recommendedWakeWindows = windows,
            stepCountToday = stepCount,
            healthConnectAvailable = hcAvailable
        )
    }

    fun readStepSensorSnapshot(onSteps: (Int) -> Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val stepSensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: run {
            onSteps(0)
            return
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val steps = event?.values?.firstOrNull()?.roundToInt() ?: 0
                sm.unregisterListener(this)
                onSteps(steps)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun openUsageAccessSettings() {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openAccessibilitySettings() {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openDndPolicySettings() {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppDetailsSettings(context.packageName)
            }
        }
    }

    fun openHealthConnectSettings() {
        val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.packageManager.resolveActivity(intent, 0) != null) {
            context.startActivity(intent)
        } else {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    private fun isKnownSocialOrBrowser(pkg: String): Boolean {
        val lower = pkg.lowercase()
        return lower.contains("tiktok") || lower.contains("musically") ||
            lower.contains("instagram") || lower.contains("youtube") ||
            lower.contains("facebook") || lower.contains("twitter") ||
            lower.contains("reddit") || lower.contains("snapchat") ||
            lower.contains("chrome") || lower.contains("browser")
    }

    private fun guessCategory(pkg: String, label: String): String {
        val combined = "$pkg $label".lowercase()
        return when {
            combined.contains("tiktok") || combined.contains("musically") ||
                combined.contains("instagram") || combined.contains("facebook") ||
                combined.contains("twitter") || combined.contains("reddit") ||
                combined.contains("snapchat") || combined.contains("threads") -> "social"
            combined.contains("youtube") || combined.contains("netflix") ||
                combined.contains("twitch") || combined.contains("video") -> "video"
            combined.contains("chrome") || combined.contains("browser") ||
                combined.contains("firefox") || combined.contains("opera") -> "browser"
            combined.contains("game") || combined.contains("pubg") ||
                combined.contains("roblox") -> "gaming"
            else -> "other"
        }
    }
}
