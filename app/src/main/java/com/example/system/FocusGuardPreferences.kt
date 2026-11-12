package com.example.system

import android.content.Context
import com.example.data.model.AppLimitRule
import com.example.data.model.BlockedSite
import com.example.data.model.UserWorkspaceConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class GuardEvalResult(
    val shouldBlock: Boolean,
    val reasonTitle: String = "",
    val reasonDetail: String = "",
    val unlockTimeLabel: String = ""
)

data class SavedScheduleConfig(
    val bedHour: Int,
    val bedMinute: Int,
    val wakeHour: Int,
    val wakeMinute: Int,
    val inactivityHours: Int
)

class FocusGuardPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "focus_guard_runtime",
        Context.MODE_PRIVATE
    )

    fun syncState(
        config: UserWorkspaceConfig,
        appRules: List<AppLimitRule>,
        blockedSites: List<BlockedSite>
    ) {
        val rulesArray = JSONArray()
        appRules.forEach { rule ->
            rulesArray.put(
                JSONObject().apply {
                    put("pkg", rule.packageName)
                    put("name", rule.appName)
                    put("limit", rule.dailyLimitMinutes)
                    put("used", rule.todayUsedMinutes)
                    put("sleep", rule.blockDuringSleep)
                    put("deep", rule.blockDuringDeepWork)
                    put("locked", rule.isLocked)
                    put("until", rule.lockedUntilMillis)
                }
            )
        }
        val sitesArray = JSONArray()
        blockedSites.forEach { site ->
            sitesArray.put(
                JSONObject().apply {
                    put("domain", site.domain.lowercase())
                    put("label", site.label)
                    put("deep", site.blockDuringDeepWork)
                    put("always", site.blockAlways)
                }
            )
        }

        prefs.edit()
            .putInt("bed_h", config.bedtimeHour.toInt())
            .putInt("bed_m", config.bedtimeMinute.toInt())
            .putInt("wake_h", config.wakeHour.toInt())
            .putInt("wake_m", config.wakeMinute.toInt())
            .putBoolean("sleep_lock", config.sleepLockEnabled)
            .putLong("deep_until", config.deepWorkActiveUntilMs)
            .putInt("inactivity_hours", config.inactivityAlertHours.toInt())
            .putBoolean("inactivity_fullscreen", config.inactivityFullScreenAlarm)
            .putString("rules_json", rulesArray.toString())
            .putString("sites_json", sitesArray.toString())
            .apply()
    }

    fun touchUserActivity() {
        prefs.edit().putLong("last_activity_ms", System.currentTimeMillis()).apply()
    }

    fun getLastActivityMs(): Long = prefs.getLong("last_activity_ms", System.currentTimeMillis())

    fun getInactivityConfig(): Pair<Int, Boolean> =
        prefs.getInt("inactivity_hours", 3) to prefs.getBoolean("inactivity_fullscreen", false)

    fun getScheduleConfig(): SavedScheduleConfig = SavedScheduleConfig(
        bedHour = prefs.getInt("bed_h", 22),
        bedMinute = prefs.getInt("bed_m", 30),
        wakeHour = prefs.getInt("wake_h", 6),
        wakeMinute = prefs.getInt("wake_m", 30),
        inactivityHours = prefs.getInt("inactivity_hours", 3)
    )

    fun isSleepWindowNow(): Boolean {
        if (!prefs.getBoolean("sleep_lock", true)) return false
        val bedMin = prefs.getInt("bed_h", 22) * 60 + prefs.getInt("bed_m", 30)
        val wakeMin = prefs.getInt("wake_h", 6) * 60 + prefs.getInt("wake_m", 30)
        val cal = Calendar.getInstance()
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return if (bedMin > wakeMin) {
            nowMin >= bedMin || nowMin < wakeMin
        } else {
            nowMin in bedMin until wakeMin
        }
    }

    fun isDeepWorkActiveNow(): Boolean {
        return prefs.getLong("deep_until", 0L) > System.currentTimeMillis()
    }

    fun wakeTimeLabel(): String = String.format(
        "%02d:%02d",
        prefs.getInt("wake_h", 6),
        prefs.getInt("wake_m", 30)
    )

    fun evaluatePackage(packageName: String, liveUsedMinutes: Long? = null): GuardEvalResult {
        val raw = prefs.getString("rules_json", "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
        val now = System.currentTimeMillis()
        val inSleep = isSleepWindowNow()
        val inDeep = isDeepWorkActiveNow()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("pkg") != packageName) continue
            val appName = obj.optString("name", packageName)
            val limit = obj.optLong("limit", 30L)
            val used = maxOf(obj.optLong("used", 0L), liveUsedMinutes ?: 0L)
            val blockSleep = obj.optBoolean("sleep", true)
            val blockDeep = obj.optBoolean("deep", true)
            val locked = obj.optBoolean("locked", false)
            val until = obj.optLong("until", 0L)

            if (locked && (until == 0L || until > now)) {
                return GuardEvalResult(
                    shouldBlock = true,
                    reasonTitle = "$appName Locked",
                    reasonDetail = "App is manually locked until your scheduled unlock.",
                    unlockTimeLabel = wakeTimeLabel()
                )
            }
            if (inSleep && blockSleep) {
                return GuardEvalResult(
                    shouldBlock = true,
                    reasonTitle = "Sleep Lockout: $appName",
                    reasonDetail = "Doomscroll guard active during your sleep schedule. Locked until wake-up.",
                    unlockTimeLabel = wakeTimeLabel()
                )
            }
            if (inDeep && blockDeep) {
                return GuardEvalResult(
                    shouldBlock = true,
                    reasonTitle = "Deep-Work Shield: $appName",
                    reasonDetail = "Blocked during your active Deep-Work focus sprint.",
                    unlockTimeLabel = "End of Sprint"
                )
            }
            if (used >= limit) {
                return GuardEvalResult(
                    shouldBlock = true,
                    reasonTitle = "Daily Limit Reached: $appName",
                    reasonDetail = "Used $used min of $limit min daily limit. Locked until tomorrow / wake-up.",
                    unlockTimeLabel = wakeTimeLabel()
                )
            }
        }
        return GuardEvalResult(shouldBlock = false)
    }

    fun evaluateUrlText(urlText: String): GuardEvalResult {
        val lower = urlText.lowercase()
        val raw = prefs.getString("sites_json", "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
        val inDeep = isDeepWorkActiveNow()
        val inSleep = isSleepWindowNow()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val domain = obj.optString("domain").lowercase().trim()
            if (domain.isEmpty()) continue
            if (lower.contains(domain)) {
                val always = obj.optBoolean("always", false)
                val deep = obj.optBoolean("deep", true)
                if (always || (deep && (inDeep || inSleep))) {
                    val label = obj.optString("label", domain)
                    return GuardEvalResult(
                        shouldBlock = true,
                        reasonTitle = "Website Blocked: $label",
                        reasonDetail = "THE MASTER MIND Gateway intercepted '$domain' during your protected schedule.",
                        unlockTimeLabel = if (inSleep) wakeTimeLabel() else "Session End"
                    )
                }
            }
        }
        return GuardEvalResult(shouldBlock = false)
    }
}
