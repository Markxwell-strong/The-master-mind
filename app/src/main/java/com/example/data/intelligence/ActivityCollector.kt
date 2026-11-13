package com.example.data.intelligence

import com.example.data.local.ActivityRecordDao
import com.example.data.local.ActivityRecordEntity
import com.example.data.model.DiaryEntry
import com.example.data.model.FocusSession
import com.example.data.model.HabitItem
import com.example.data.model.NoteItem
import com.example.system.SleepCircadianInsights
import com.example.system.UsageMonitor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ActivityCollector(
    private val usageMonitor: UsageMonitor,
    private val activityDao: ActivityRecordDao
) {
    suspend fun collectAndStore(
        habits: List<HabitItem>,
        notes: List<NoteItem>,
        focusSessions: List<FocusSession>,
        diaryEntries: List<DiaryEntry>,
        sleepInsights: SleepCircadianInsights,
        lookbackDays: Int = 7
    ): CollectSummary {
        val now = System.currentTimeMillis()
        val todayKey = dateKeyFor(now)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val normalized = mutableListOf<ActivityRecordEntity>()

        // 1. Collect UsageStatsManager records for today + recent days (within retention/lookback)
        var latestUsageGranted = false
        for (offset in -(lookbackDays - 1).coerceAtLeast(0)..0) {
            val snap = usageMonitor.collectDaySnapshot(offset)
            if (snap.usageAccessGranted) latestUsageGranted = true
            snap.appStats.take(25).forEach { app ->
                val peakHour = app.hourlyMinutes.maxByOrNull { it.value }?.key ?: currentHour
                normalized.add(
                    ActivityRecordEntity(
                        id = "app_${snap.dateKey}_${app.packageName.hashCode()}",
                        timestampMs = app.lastUsedMs.coerceAtLeast(snap.windowStartMs + 60_000L),
                        dateKey = snap.dateKey,
                        hourOfDay = peakHour,
                        sourceType = "app_usage",
                        itemKey = app.packageName,
                        label = app.appName,
                        category = app.category,
                        durationMinutes = app.foregroundMinutes,
                        launchCount = app.launchCount,
                        completed = false,
                        overdue = false,
                        productivityWeight = app.productivityWeight,
                        details = "${app.foregroundMinutes}m foreground • ${app.launchCount} launches"
                    )
                )
            }
        }

        // 2. Habits (completed & pending today)
        habits.forEach { habit ->
            val isDone = habit.completedToday >= habit.targetPerDay
            val hour = habit.reminderTime.substringBefore(":").toIntOrNull()?.coerceIn(0, 23) ?: currentHour
            normalized.add(
                ActivityRecordEntity(
                    id = "hab_${todayKey}_${habit.id.ifBlank { habit.title.hashCode() }}",
                    timestampMs = habit.updatedAt?.toDate()?.time ?: now,
                    dateKey = todayKey,
                    hourOfDay = hour,
                    sourceType = "habit",
                    itemKey = habit.id.ifBlank { habit.title },
                    label = habit.title,
                    category = habit.category,
                    durationMinutes = 0L,
                    launchCount = habit.completedToday.toInt(),
                    completed = isDone,
                    overdue = false,
                    productivityWeight = if (isDone) 0.95f else 0.1f,
                    details = "${habit.completedToday}/${habit.targetPerDay} today • ${habit.streakDays}d streak"
                )
            )
        }

        // 3. Tasks & Notebook Reminders (completion & overdue status)
        notes.forEach { note ->
            val createdMs = note.createdAt?.toDate()?.time ?: now
            val isOverdue = !note.isCompleted && note.reminderAtMillis > 0L && note.reminderAtMillis < now
            val refMs = if (note.reminderAtMillis > 0L) note.reminderAtMillis else createdMs
            val dKey = dateKeyFor(createdMs)
            val hr = Calendar.getInstance().apply { timeInMillis = refMs }.get(Calendar.HOUR_OF_DAY)
            normalized.add(
                ActivityRecordEntity(
                    id = "task_${note.id.ifBlank { note.title.hashCode() }}",
                    timestampMs = note.updatedAt?.toDate()?.time ?: createdMs,
                    dateKey = dKey,
                    hourOfDay = hr,
                    sourceType = "task",
                    itemKey = note.id.ifBlank { note.title },
                    label = note.title,
                    category = note.category,
                    durationMinutes = 0L,
                    launchCount = 1,
                    completed = note.isCompleted,
                    overdue = isOverdue,
                    productivityWeight = when {
                        note.isCompleted -> 0.85f
                        isOverdue -> -0.4f
                        else -> 0.2f
                    },
                    details = when {
                        note.isCompleted -> "Completed ${note.category}"
                        isOverdue -> "Overdue reminder (${note.category})"
                        else -> "Pending ${note.category}"
                    }
                )
            )
        }

        // 4. Deep-Work / Pomodoro Focus Sessions
        focusSessions.forEach { session ->
            val ts = session.createdAt?.toDate()?.time ?: now
            val hr = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
            normalized.add(
                ActivityRecordEntity(
                    id = "focus_${session.id.ifBlank { "${ts}_${session.title.hashCode()}" }}",
                    timestampMs = ts,
                    dateKey = dateKeyFor(ts),
                    hourOfDay = hr,
                    sourceType = "focus_session",
                    itemKey = session.id.ifBlank { session.title },
                    label = session.title.ifBlank { "Deep-Work Sprint" },
                    category = "focus",
                    durationMinutes = session.durationMinutes,
                    launchCount = 1,
                    completed = session.completed,
                    overdue = false,
                    productivityWeight = if (session.completed) 1.0f else 0.4f,
                    details = "${session.durationMinutes}m ${session.mode} • ${session.distractionsBlocked} distractions shielded"
                )
            )
        }

        // 5. Diary Logs
        diaryEntries.forEach { entry ->
            val ts = entry.updatedAt?.toDate()?.time ?: entry.createdAt?.toDate()?.time ?: now
            val hr = Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
            normalized.add(
                ActivityRecordEntity(
                    id = "diary_${entry.dateKey}",
                    timestampMs = ts,
                    dateKey = entry.dateKey,
                    hourOfDay = hr,
                    sourceType = "diary",
                    itemKey = entry.dateKey,
                    label = "Daily Reflection",
                    category = "diary",
                    durationMinutes = (entry.onlineHours * 60.0).toLong(),
                    launchCount = 1,
                    completed = entry.activitiesText.isNotBlank(),
                    overdue = false,
                    productivityWeight = ((entry.aiScore.takeIf { it >= 0 } ?: 65L) / 100f).coerceIn(0f, 1f),
                    details = "Mood ${entry.moodScore}/5 • Energy ${entry.energyScore}/5"
                )
            )
        }

        // 6. Sleep Telemetry Snapshot for Today
        normalized.add(
            ActivityRecordEntity(
                id = "sleep_$todayKey",
                timestampMs = now,
                dateKey = todayKey,
                hourOfDay = 6,
                sourceType = "sleep",
                itemKey = "circadian_sleep",
                label = "Sleep Schedule (${String.format(Locale.US, "%.1fh", sleepInsights.estimatedSleepHours)})",
                category = "sleep",
                durationMinutes = (sleepInsights.estimatedSleepHours * 60.0).toLong(),
                launchCount = 1,
                completed = sleepInsights.estimatedSleepHours >= 7.0,
                overdue = false,
                productivityWeight = if (sleepInsights.estimatedSleepHours >= 7.0) 0.7f else -0.3f,
                details = "Bedtime ${sleepInsights.estimatedSleepOnsetLabel} • Steps ${sleepInsights.stepCountToday}"
            )
        )

        if (normalized.isNotEmpty()) {
            activityDao.upsertAll(normalized)
        }

        return CollectSummary(
            collectedCount = normalized.size,
            usageAccessGranted = latestUsageGranted,
            todayKey = todayKey
        )
    }

    private fun dateKeyFor(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
}

data class CollectSummary(
    val collectedCount: Int,
    val usageAccessGranted: Boolean,
    val todayKey: String
)
