package com.example.data.intelligence

import com.example.data.local.ActivityRecordEntity
import com.example.data.local.BehaviouralPatternEntity
import com.example.data.model.AppLimitRule
import com.example.data.model.HabitItem
import com.example.data.model.NoteItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class PersonalIntelligenceProfile(
    val peakProductiveWindow: String,
    val peakActiveWindow: String,
    val primaryFocusAnchor: String,
    val primaryDistractionTrigger: String,
    val habitConsistencyPercent: Int,
    val averageDailyScreenMinutes: Long,
    val sleepAlignmentLabel: String,
    val productivityMomentumLabel: String,
    val observationDays: Int
)

class PatternEngine {

    fun detectPatterns(
        records: List<ActivityRecordEntity>,
        analytics: AnalyticsSnapshot,
        habits: List<HabitItem>,
        notes: List<NoteItem>,
        appRules: List<AppLimitRule>,
        existingVerified: List<BehaviouralPatternEntity> = emptyList(),
        nowMs: Long = System.currentTimeMillis()
    ): Pair<PersonalIntelligenceProfile, List<BehaviouralPatternEntity>> {
        val verifiedMap = existingVerified.filter { it.userVerified }.associateBy { it.id }
        val patterns = mutableListOf<BehaviouralPatternEntity>()
        val daysObserved = analytics.observationDays.coerceAtLeast(1)

        // 1. Most active & most productive hours
        val prodHour = analytics.peakProductiveHour
        val activeHour = analytics.peakActiveHour
        if (prodHour != null || activeHour != null) {
            val hr = prodHour ?: activeHour ?: 9
            val prodMins = analytics.hourlyActivityMinutes[hr] ?: 0L
            patterns += BehaviouralPatternEntity(
                id = "pat_peak_hours",
                category = "peak_hours",
                title = "Peak Cognitive Window: ${formatHourRange(hr)}",
                summary = if (prodHour != null) {
                    "Your highest weighted focus & completion output occurs around ${formatHourRange(prodHour)}."
                } else {
                    "Device & task activity peaks around ${formatHourRange(hr)}."
                },
                evidence = "$prodMins min of recorded focus/activity in ${formatHourRange(hr)} across $daysObserved day(s)",
                comparisonPeriod = "Last ${daysObserved.coerceAtMost(7)} days",
                confidence = calibrateConfidence(daysObserved, minSamples = 2, base = 0.56f),
                detectedAtMs = nowMs
            )
        }

        // 2. Frequently used applications & launch frequency
        val appTotals = records.filter { it.sourceType == "app_usage" }
            .groupBy { it.itemKey }
            .mapValues { (_, list) ->
                Triple(
                    list.first().label,
                    list.sumOf { it.durationMinutes },
                    list.sumOf { it.launchCount }
                )
            }
            .values
            .sortedByDescending { it.second }

        val topFrequent = appTotals.firstOrNull()
        if (topFrequent != null && (topFrequent.second > 0L || topFrequent.third > 0)) {
            patterns += BehaviouralPatternEntity(
                id = "pat_frequent_apps",
                category = "frequent_apps",
                title = "Most Frequent App: ${topFrequent.first}",
                summary = "${topFrequent.first} accounts for ${topFrequent.second}m foreground time and ${topFrequent.third} launches.",
                evidence = "Top 3 apps: ${appTotals.take(3).joinToString(", ") { "${it.first} (${it.second}m / ${it.third}x)" }}",
                comparisonPeriod = "Current observation window ($daysObserved d)",
                confidence = calibrateConfidence(topFrequent.third, minSamples = 3, base = 0.62f),
                detectedAtMs = nowMs
            )
        }

        // 3. Repeated distraction sources (social/video/gaming/locked apps)
        val distractions = records.filter {
            it.sourceType == "app_usage" && it.productivityWeight < -0.2f
        }.groupBy { it.itemKey }
            .mapValues { (_, list) ->
                Triple(list.first().label, list.sumOf { it.durationMinutes }, list.sumOf { it.launchCount })
            }
            .values
            .sortedByDescending { it.second + it.third * 2L }

        val topDistractor = distractions.firstOrNull()
        if (topDistractor != null && (topDistractor.second >= 10L || topDistractor.third >= 4)) {
            val matchingRule = appRules.firstOrNull {
                it.appName.equals(topDistractor.first, ignoreCase = true)
            }
            val limitNote = if (matchingRule != null) {
                "Daily limit configured at ${matchingRule.dailyLimitMinutes}m."
            } else {
                "No daily limit rule configured yet."
            }
            patterns += BehaviouralPatternEntity(
                id = "pat_distraction_loop",
                category = "distraction_loop",
                title = "Repeated Distraction Loop: ${topDistractor.first}",
                summary = "${topDistractor.first} was launched ${topDistractor.third} times (${topDistractor.second}m total). $limitNote",
                evidence = "${topDistractor.third} launches, ${topDistractor.second}m cumulative leisure/social time",
                comparisonPeriod = "Recent $daysObserved day(s)",
                confidence = calibrateConfidence(topDistractor.third, minSamples = 4, base = 0.64f),
                detectedAtMs = nowMs
            )
        }

        // 4. Habit consistency
        val completedHabits = habits.count { it.completedToday >= it.targetPerDay }
        val avgStreak = if (habits.isEmpty()) 0.0 else habits.map { it.streakDays }.average()
        val habitConsistency = if (habits.isEmpty()) 0 else ((completedHabits * 100.0) / habits.size).roundToInt()
        if (habits.isNotEmpty()) {
            val bestHabit = habits.maxByOrNull { it.streakDays }
            patterns += BehaviouralPatternEntity(
                id = "pat_habit_consistency",
                category = "habit_consistency",
                title = "Habit Adherence at $habitConsistency%",
                summary = "$completedHabits of ${habits.size} daily habits completed today; average streak is ${String.format(Locale.US, "%.1f", avgStreak)} days.",
                evidence = "Strongest streak: ${bestHabit?.title ?: "None"} (${bestHabit?.streakDays ?: 0}d)",
                comparisonPeriod = "Daily checklist + active streaks",
                confidence = calibrateConfidence(habits.size, minSamples = 2, base = 0.72f),
                detectedAtMs = nowMs
            )
        }

        // 5. Routine shifts (current 3 days vs older days)
        val orderedDays = analytics.weeklySeries.filter { it.score > 0 || it.screenMinutes > 0 }
        if (orderedDays.size >= 4) {
            val recentHalf = orderedDays.takeLast(orderedDays.size / 2)
            val earlierHalf = orderedDays.take(orderedDays.size / 2)
            val recentFocusAvg = recentHalf.map { it.focusMinutes }.average()
            val earlierFocusAvg = earlierHalf.map { it.focusMinutes }.average()
            val diffFocus = (recentFocusAvg - earlierFocusAvg).roundToInt()
            if (abs(diffFocus) >= 10) {
                patterns += BehaviouralPatternEntity(
                    id = "pat_routine_shift",
                    category = "routine_shift",
                    title = if (diffFocus > 0) "Upward Focus Routine Shift" else "Focus Routine Contraction",
                    summary = if (diffFocus > 0) {
                        "Deep-work focus time increased by ${diffFocus}m/day compared to the earlier half of the week."
                    } else {
                        "Deep-work focus time dropped by ${abs(diffFocus)}m/day compared to earlier days."
                    },
                    evidence = "Recent avg ${recentFocusAvg.roundToInt()}m/day vs earlier ${earlierFocusAvg.roundToInt()}m/day",
                    comparisonPeriod = "${earlierHalf.first().dayLabel}-${earlierHalf.last().dayLabel} vs ${recentHalf.first().dayLabel}-${recentHalf.last().dayLabel}",
                    confidence = calibrateConfidence(orderedDays.size, minSamples = 4, base = 0.60f),
                    detectedAtMs = nowMs
                )
            }
        }

        // 6. Productivity fluctuations across days
        val activeScores = analytics.weeklySeries.filter { it.score > 0 }.map { it.score }
        if (activeScores.size >= 2) {
            val minS = activeScores.minOrNull() ?: 50
            val maxS = activeScores.maxOrNull() ?: 50
            val spread = maxS - minS
            val deltaStr = if (analytics.scoreDeltaVsYesterday >= 0) "+${analytics.scoreDeltaVsYesterday}" else "${analytics.scoreDeltaVsYesterday}"
            patterns += BehaviouralPatternEntity(
                id = "pat_productivity_trend",
                category = "productivity_trend",
                title = if (spread <= 12) "Steady Productivity Rhythm ($deltaStr pts)" else "High Productivity Variance ($spread pt swing)",
                summary = if (spread <= 12) {
                    "Daily productivity scores stay within a consistent $minS–$maxS band."
                } else {
                    "Productivity swings between $minS and $maxS across days; today moved $deltaStr points vs yesterday."
                },
                evidence = "7-day range $minS–$maxS • Today: ${analytics.dailyProductivityScore}/100 ($deltaStr vs yesterday)",
                comparisonPeriod = "7-day rolling window",
                confidence = calibrateConfidence(activeScores.size, minSamples = 2, base = 0.66f),
                detectedAtMs = nowMs
            )
        }

        // 7. Recurring task delays / overdue reminders
        val overdueNotes = notes.filter {
            !it.isCompleted && it.reminderAtMillis > 0L && it.reminderAtMillis < nowMs
        }
        if (overdueNotes.isNotEmpty()) {
            patterns += BehaviouralPatternEntity(
                id = "pat_task_delay",
                category = "task_delay",
                title = "${overdueNotes.size} Overdue Task Reminder(s)",
                summary = "Unresolved items past scheduled reminder time are creating open cognitive loops.",
                evidence = "Overdue: ${overdueNotes.take(3).joinToString(", ") { it.title }}",
                comparisonPeriod = "Active reminders vs current time",
                confidence = 0.90f,
                detectedAtMs = nowMs
            )
        }

        // 8. Screen-time increases/decreases
        val comp = analytics.weekComparison
        if (comp.currentScreenMinutes > 0L || comp.previousScreenMinutes > 0L) {
            val deltaPct = comp.screenDeltaPercent
            val direction = when {
                deltaPct >= 10 -> "Screen Time Increased (+$deltaPct%)"
                deltaPct <= -10 -> "Screen Time Decreased ($deltaPct%)"
                else -> "Screen Time Stable (${if (deltaPct >= 0) "+$deltaPct" else "$deltaPct"}%)"
            }
            patterns += BehaviouralPatternEntity(
                id = "pat_screen_time_shift",
                category = "screen_time_shift",
                title = direction,
                summary = "Total screen time is ${comp.currentScreenMinutes}m this 7-day cycle vs ${comp.previousScreenMinutes}m previously.",
                evidence = "${comp.currentScreenMinutes}m current vs ${comp.previousScreenMinutes}m prior • Today ${analytics.todayScreenMinutes}m (${analytics.todayAppLaunches} launches)",
                comparisonPeriod = "${comp.currentLabel} vs ${comp.previousLabel}",
                confidence = calibrateConfidence(daysObserved, minSamples = 2, base = 0.65f),
                detectedAtMs = nowMs
            )
        }

        // 9. Sleep duration & late-night usage recovery
        val lateNightUsage = records.filter {
            it.sourceType == "app_usage" && (it.hourOfDay >= 23 || it.hourOfDay <= 4) && it.productivityWeight < 0f
        }.sumOf { it.durationMinutes }
        val sleepHrs = analytics.estimatedSleepHours
        if (sleepHrs > 0.0) {
            val sleepStatus = when {
                lateNightUsage >= 20L -> "Late-Night Screen Friction (${lateNightUsage}m after 23:00)"
                sleepHrs < 6.8 -> "Short Sleep Window (${String.format(Locale.US, "%.1fh", sleepHrs)})"
                else -> "Healthy Sleep Allocation (${String.format(Locale.US, "%.1fh", sleepHrs)})"
            }
            patterns += BehaviouralPatternEntity(
                id = "pat_sleep_recovery",
                category = "sleep_recovery",
                title = sleepStatus,
                summary = if (lateNightUsage >= 20L) {
                    "Detected ${lateNightUsage}m of distracting usage between 23:00–04:00, which risks sleep onset delay."
                } else {
                    "Configured sleep duration is ${String.format(Locale.US, "%.1fh", sleepHrs)} with low late-night screen disruption."
                },
                evidence = "Sleep ${String.format(Locale.US, "%.1fh", sleepHrs)} • Late leisure screen: ${lateNightUsage}m",
                comparisonPeriod = "Bedtime & late-hour UsageStats",
                confidence = 0.78f,
                detectedAtMs = nowMs
            )
        }

        // Merge with any user edits/verifications
        val finalPatterns = patterns.map { detected ->
            val verified = verifiedMap[detected.id]
            if (verified != null) {
                detected.copy(
                    userVerified = true,
                    userNote = verified.userNote,
                    summary = verified.userNote.ifBlank { detected.summary },
                    confidence = 0.98f
                )
            } else {
                detected
            }
        }

        val profile = PersonalIntelligenceProfile(
            peakProductiveWindow = analytics.peakProductiveHour?.let { formatHourRange(it) } ?: "Needs more data",
            peakActiveWindow = analytics.peakActiveHour?.let { formatHourRange(it) } ?: "Needs more data",
            primaryFocusAnchor = if (analytics.todayFocusMinutes > 0) "${analytics.todayFocusMinutes}m deep work today" else "No focus sprint logged yet",
            primaryDistractionTrigger = topDistractor?.let { "${it.first} (${it.second}m / ${it.third}x)" } ?: "None detected",
            habitConsistencyPercent = habitConsistency,
            averageDailyScreenMinutes = if (daysObserved > 0) analytics.weeklySeries.sumOf { it.screenMinutes } / daysObserved else 0L,
            sleepAlignmentLabel = String.format(Locale.US, "%.1fh sleep target", sleepHrs),
            productivityMomentumLabel = if (analytics.scoreDeltaVsYesterday >= 0) {
                "+${analytics.scoreDeltaVsYesterday} pts vs yesterday"
            } else {
                "${analytics.scoreDeltaVsYesterday} pts vs yesterday"
            },
            observationDays = daysObserved
        )

        return profile to finalPatterns
    }

    private fun calibrateConfidence(samples: Int, minSamples: Int, base: Float): Float {
        val ratio = (samples.toFloat() / (minSamples * 3f)).coerceIn(0f, 1f)
        return (base + ratio * (0.94f - base)).coerceIn(0.35f, 0.95f)
    }

    private fun formatHourRange(hour: Int): String {
        val h = hour.coerceIn(0, 23)
        val next = (h + 1) % 24
        return String.format(Locale.US, "%02d:00–%02d:00", h, next)
    }
}
