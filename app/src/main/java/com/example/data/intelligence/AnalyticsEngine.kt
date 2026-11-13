package com.example.data.intelligence

import com.example.data.local.ActivityRecordEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class DailyProductivityPoint(
    val dateKey: String,
    val dayLabel: String,
    val score: Int,
    val focusMinutes: Long,
    val screenMinutes: Long,
    val distractionMinutes: Long
)

data class CategoryBreakdown(
    val category: String,
    val minutes: Long,
    val launches: Int
)

data class PeriodComparison(
    val currentLabel: String,
    val previousLabel: String,
    val currentAvgScore: Int,
    val previousAvgScore: Int,
    val scoreDelta: Int,
    val currentScreenMinutes: Long,
    val previousScreenMinutes: Long,
    val screenDeltaPercent: Int,
    val currentFocusMinutes: Long,
    val previousFocusMinutes: Long,
    val focusDeltaPercent: Int,
    val currentHabitsDone: Int,
    val previousHabitsDone: Int
)

data class AnalyticsSnapshot(
    val todayKey: String,
    val dailyProductivityScore: Int,
    val scoreDeltaVsYesterday: Int,
    val todayFocusMinutes: Long,
    val todayScreenMinutes: Long,
    val todayProductiveAppMinutes: Long,
    val todayDistractionMinutes: Long,
    val todayAppLaunches: Int,
    val habitsCompletedToday: Int,
    val habitsTotalToday: Int,
    val tasksPending: Int,
    val tasksOverdue: Int,
    val tasksCompleted: Int,
    val estimatedSleepHours: Double,
    val peakActiveHour: Int?,
    val peakProductiveHour: Int?,
    val hourlyActivityMinutes: Map<Int, Long>,
    val hourlyProductivityScore: Map<Int, Float>,
    val categoryBreakdown: List<CategoryBreakdown>,
    val topLaunchedApps: List<ActivityRecordEntity>,
    val weeklySeries: List<DailyProductivityPoint>,
    val weekComparison: PeriodComparison,
    val observationDays: Int
)

class AnalyticsEngine {

    fun compute(
        records: List<ActivityRecordEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): AnalyticsSnapshot {
        val todayKey = dateKeyFor(nowMs)
        val dayKeysLast14 = recentDateKeys(nowMs, 14)
        val currentWeekKeys = dayKeysLast14.takeLast(7).toSet()
        val previousWeekKeys = dayKeysLast14.take(7).toSet()
        val yesterdayKey = dayKeysLast14.getOrNull(dayKeysLast14.size - 2) ?: todayKey

        val byDay = records.groupBy { it.dateKey }
        val todayRecords = byDay[todayKey].orEmpty()

        val todayScore = computeDayScore(todayRecords)
        val yesterdayScore = if (byDay.containsKey(yesterdayKey)) {
            computeDayScore(byDay[yesterdayKey].orEmpty())
        } else {
            todayScore
        }

        val todayAppUsage = todayRecords.filter { it.sourceType == "app_usage" }
        val todayScreenMins = todayAppUsage.sumOf { it.durationMinutes }
        val todayProductiveMins = todayAppUsage
            .filter { it.productivityWeight > 0.2f }
            .sumOf { it.durationMinutes }
        val todayDistractionMins = todayAppUsage
            .filter { it.productivityWeight < -0.2f }
            .sumOf { it.durationMinutes }
        val todayLaunches = todayAppUsage.sumOf { it.launchCount }

        val todayFocusMins = todayRecords
            .filter { it.sourceType == "focus_session" }
            .sumOf { it.durationMinutes }

        val todayHabits = todayRecords.filter { it.sourceType == "habit" }
        val habitsDone = todayHabits.count { it.completed }
        val habitsTotal = todayHabits.size

        val allTasks = records.filter { it.sourceType == "task" }
        val tasksPending = allTasks.count { !it.completed }
        val tasksOverdue = allTasks.count { !it.completed && it.overdue }
        val tasksCompleted = allTasks.count { it.completed }

        val sleepRecord = todayRecords.firstOrNull { it.sourceType == "sleep" }
        val sleepHours = (sleepRecord?.durationMinutes ?: 450L) / 60.0

        // Hourly breakdown over all observed records (current 7 days prioritized)
        val recentRecords = records.filter { it.dateKey in currentWeekKeys }
        val hourlySource = if (recentRecords.isNotEmpty()) recentRecords else records

        val hourlyActivity = mutableMapOf<Int, Long>()
        val hourlyProdWeighted = mutableMapOf<Int, Double>()
        hourlySource.forEach { rec ->
            val hr = rec.hourOfDay.coerceIn(0, 23)
            val mins = rec.durationMinutes.coerceAtLeast(if (rec.completed) 15L else 5L)
            hourlyActivity[hr] = (hourlyActivity[hr] ?: 0L) + mins
            val contribution = rec.productivityWeight * mins.toDouble()
            hourlyProdWeighted[hr] = (hourlyProdWeighted[hr] ?: 0.0) + contribution
        }
        val hourlyProdFloat = hourlyProdWeighted.mapValues { it.value.toFloat() }
        val peakActiveHour = hourlyActivity.maxByOrNull { it.value }?.key
        val peakProductiveHour = hourlyProdWeighted.filter { it.value > 0.0 }.maxByOrNull { it.value }?.key

        val categoryBreakdown = todayAppUsage
            .groupBy { it.category }
            .map { (cat, list) ->
                CategoryBreakdown(
                    category = cat,
                    minutes = list.sumOf { it.durationMinutes },
                    launches = list.sumOf { it.launchCount }
                )
            }
            .sortedByDescending { it.minutes }

        val topLaunched = todayAppUsage
            .sortedWith(compareByDescending<ActivityRecordEntity> { it.launchCount }.thenByDescending { it.durationMinutes })
            .take(8)

        val weeklySeries = dayKeysLast14.takeLast(7).map { key ->
            val dayRecs = byDay[key].orEmpty()
            val apps = dayRecs.filter { it.sourceType == "app_usage" }
            val focus = dayRecs.filter { it.sourceType == "focus_session" }.sumOf { it.durationMinutes }
            val screen = apps.sumOf { it.durationMinutes }
            val distract = apps.filter { it.productivityWeight < -0.2f }.sumOf { it.durationMinutes }
            DailyProductivityPoint(
                dateKey = key,
                dayLabel = shortDayLabel(key),
                score = if (dayRecs.isEmpty()) 0 else computeDayScore(dayRecs),
                focusMinutes = focus,
                screenMinutes = screen,
                distractionMinutes = distract
            )
        }

        val currentWeekRecs = records.filter { it.dateKey in currentWeekKeys }
        val prevWeekRecs = records.filter { it.dateKey in previousWeekKeys }
        val weekComparison = buildComparison(
            currentLabel = "This 7d",
            previousLabel = "Prior 7d",
            currentRecs = currentWeekRecs,
            previousRecs = prevWeekRecs,
            currentDayKeys = currentWeekKeys,
            previousDayKeys = previousWeekKeys
        )

        return AnalyticsSnapshot(
            todayKey = todayKey,
            dailyProductivityScore = todayScore,
            scoreDeltaVsYesterday = todayScore - yesterdayScore,
            todayFocusMinutes = todayFocusMins,
            todayScreenMinutes = todayScreenMins,
            todayProductiveAppMinutes = todayProductiveMins,
            todayDistractionMinutes = todayDistractionMins,
            todayAppLaunches = todayLaunches,
            habitsCompletedToday = habitsDone,
            habitsTotalToday = habitsTotal,
            tasksPending = tasksPending,
            tasksOverdue = tasksOverdue,
            tasksCompleted = tasksCompleted,
            estimatedSleepHours = sleepHours,
            peakActiveHour = peakActiveHour,
            peakProductiveHour = peakProductiveHour,
            hourlyActivityMinutes = hourlyActivity,
            hourlyProductivityScore = hourlyProdFloat,
            categoryBreakdown = categoryBreakdown,
            topLaunchedApps = topLaunched,
            weeklySeries = weeklySeries,
            weekComparison = weekComparison,
            observationDays = byDay.keys.size
        )
    }

    fun computeDayScore(dayRecords: List<ActivityRecordEntity>): Int {
        if (dayRecords.isEmpty()) return 50
        var score = 55.0

        val focusMins = dayRecords.filter { it.sourceType == "focus_session" }.sumOf { it.durationMinutes }
        score += (focusMins / 6.0).coerceAtMost(22.0)

        val habits = dayRecords.filter { it.sourceType == "habit" }
        if (habits.isNotEmpty()) {
            val ratio = habits.count { it.completed }.toDouble() / habits.size
            score += (ratio * 18.0) - 4.0
        }

        val tasks = dayRecords.filter { it.sourceType == "task" }
        val completedTasks = tasks.count { it.completed }
        val overdueTasks = tasks.count { !it.completed && it.overdue }
        score += (completedTasks * 3.0).coerceAtMost(10.0)
        score -= (overdueTasks * 4.5).coerceAtMost(18.0)

        val appUsage = dayRecords.filter { it.sourceType == "app_usage" }
        val distractMins = appUsage.filter { it.productivityWeight < -0.2f }.sumOf { it.durationMinutes }
        val prodMins = appUsage.filter { it.productivityWeight > 0.2f }.sumOf { it.durationMinutes }
        score += (prodMins / 12.0).coerceAtMost(8.0)
        score -= (distractMins / 8.0).coerceAtMost(24.0)

        val diary = dayRecords.firstOrNull { it.sourceType == "diary" }
        if (diary != null && diary.completed) {
            score += 4.0
        }

        val sleep = dayRecords.firstOrNull { it.sourceType == "sleep" }
        if (sleep != null) {
            val hrs = sleep.durationMinutes / 60.0
            if (hrs in 6.8..9.0) score += 3.0 else if (hrs < 6.0) score -= 5.0
        }

        return score.roundToInt().coerceIn(5, 100)
    }

    private fun buildComparison(
        currentLabel: String,
        previousLabel: String,
        currentRecs: List<ActivityRecordEntity>,
        previousRecs: List<ActivityRecordEntity>,
        currentDayKeys: Set<String>,
        previousDayKeys: Set<String>
    ): PeriodComparison {
        val curDays = currentRecs.groupBy { it.dateKey }.filterKeys { it in currentDayKeys }
        val prevDays = previousRecs.groupBy { it.dateKey }.filterKeys { it in previousDayKeys }

        val curAvgScore = if (curDays.isEmpty()) 0 else curDays.values.map { computeDayScore(it) }.average().roundToInt()
        val prevAvgScore = if (prevDays.isEmpty()) 0 else prevDays.values.map { computeDayScore(it) }.average().roundToInt()

        val curScreen = currentRecs.filter { it.sourceType == "app_usage" }.sumOf { it.durationMinutes }
        val prevScreen = previousRecs.filter { it.sourceType == "app_usage" }.sumOf { it.durationMinutes }
        val screenDeltaPct = pctChange(curScreen, prevScreen)

        val curFocus = currentRecs.filter { it.sourceType == "focus_session" }.sumOf { it.durationMinutes }
        val prevFocus = previousRecs.filter { it.sourceType == "focus_session" }.sumOf { it.durationMinutes }
        val focusDeltaPct = pctChange(curFocus, prevFocus)

        val curHabits = currentRecs.count { it.sourceType == "habit" && it.completed }
        val prevHabits = previousRecs.count { it.sourceType == "habit" && it.completed }

        return PeriodComparison(
            currentLabel = currentLabel,
            previousLabel = previousLabel,
            currentAvgScore = curAvgScore,
            previousAvgScore = prevAvgScore,
            scoreDelta = if (prevDays.isEmpty()) 0 else curAvgScore - prevAvgScore,
            currentScreenMinutes = curScreen,
            previousScreenMinutes = prevScreen,
            screenDeltaPercent = screenDeltaPct,
            currentFocusMinutes = curFocus,
            previousFocusMinutes = prevFocus,
            focusDeltaPercent = focusDeltaPct,
            currentHabitsDone = curHabits,
            previousHabitsDone = prevHabits
        )
    }

    private fun pctChange(current: Long, previous: Long): Int {
        if (previous <= 0L) return if (current > 0L) 100 else 0
        return (((current - previous).toDouble() / previous.toDouble()) * 100.0).roundToInt()
    }

    private fun recentDateKeys(nowMs: Long, count: Int): List<String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -(count - 1))
        }
        return (0 until count).map {
            val k = sdf.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            k
        }
    }

    private fun shortDayLabel(dateKey: String): String {
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey) ?: return dateKey.takeLast(2)
            SimpleDateFormat("EEE", Locale.US).format(parsed)
        } catch (_: Exception) {
            dateKey.takeLast(2)
        }
    }

    private fun dateKeyFor(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
}
