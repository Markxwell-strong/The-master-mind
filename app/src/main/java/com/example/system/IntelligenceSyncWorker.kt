package com.example.system

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.data.ai.GeminiService
import com.example.data.intelligence.InsightManager
import com.example.data.intelligence.IntelligenceRepository
import com.example.data.preferences.AppearancePreferencesStore
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Battery-aware periodic WorkManager task that refreshes local UsageStats telemetry,
 * updates behavioural patterns, and prunes expired intelligence data according to user retention settings.
 */
class IntelligenceSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefsStore = AppearancePreferencesStore(applicationContext)
        val appearance = prefsStore.state.first()
        if (!appearance.monitoringEnabled) {
            return Result.success()
        }

        return try {
            val repo = IntelligenceRepository(applicationContext)
            val usageMonitor = UsageMonitor(applicationContext)
            val insightManager = InsightManager(usageMonitor, repo, GeminiService())
            val telemetry = DeviceTelemetryManager(applicationContext)
            val guardPrefs = FocusGuardPreferences(applicationContext)
            val sched = guardPrefs.getScheduleConfig()
            val sleepInsights = telemetry.computeSleepCircadianInsights(
                sched.bedHour,
                sched.bedMinute,
                sched.wakeHour,
                sched.wakeMinute,
                0
            )

            insightManager.refreshIntelligence(
                habits = emptyList(),
                notes = emptyList(),
                focusSessions = emptyList(),
                diaryEntries = emptyList(),
                appRules = emptyList(),
                sleepInsights = sleepInsights,
                monitoringEnabled = true,
                retentionDays = appearance.retentionDays
            )
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "master_mind_intelligence_periodic_sync"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<IntelligenceSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
