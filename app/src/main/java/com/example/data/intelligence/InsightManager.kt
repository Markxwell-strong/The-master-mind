package com.example.data.intelligence

import com.example.data.ai.DailyBriefingResult
import com.example.data.ai.GeminiService
import com.example.data.ai.IntelligenceQaAnswer
import com.example.data.local.BehaviouralPatternEntity
import com.example.data.local.IntelligenceInsightEntity
import com.example.data.model.AppLimitRule
import com.example.data.model.DiaryEntry
import com.example.data.model.FocusSession
import com.example.data.model.HabitItem
import com.example.data.model.NoteItem
import com.example.system.SleepCircadianInsights
import com.example.system.UsageMonitor
import java.util.Locale

data class IntelligencePipelineResult(
    val analytics: AnalyticsSnapshot,
    val profile: PersonalIntelligenceProfile,
    val patterns: List<BehaviouralPatternEntity>,
    val insights: List<IntelligenceInsightEntity>,
    val usageAccessGranted: Boolean
)

class InsightManager(
    private val usageMonitor: UsageMonitor,
    private val repository: IntelligenceRepository,
    private val geminiService: GeminiService
) {
    private val collector = ActivityCollector(usageMonitor, repository.activityDao)
    private val analyticsEngine = AnalyticsEngine()
    private val patternEngine = PatternEngine()

    suspend fun refreshIntelligence(
        habits: List<HabitItem>,
        notes: List<NoteItem>,
        focusSessions: List<FocusSession>,
        diaryEntries: List<DiaryEntry>,
        appRules: List<AppLimitRule>,
        sleepInsights: SleepCircadianInsights,
        monitoringEnabled: Boolean,
        retentionDays: Int
    ): IntelligencePipelineResult {
        val now = System.currentTimeMillis()
        var usageGranted = usageMonitor.hasUsageStatsPermission()

        if (monitoringEnabled) {
            val collected = collector.collectAndStore(
                habits = habits,
                notes = notes,
                focusSessions = focusSessions,
                diaryEntries = diaryEntries,
                sleepInsights = sleepInsights,
                lookbackDays = retentionDays.coerceAtMost(14)
            )
            usageGranted = collected.usageAccessGranted
            repository.enforceRetention(retentionDays, now)
        }

        val records = repository.getRecordsWindow(days = retentionDays.coerceAtMost(30), nowMs = now)
        val analytics = analyticsEngine.compute(records, nowMs = now)
        val existingPatterns = repository.patternDao.getAll()
        val (profile, patterns) = patternEngine.detectPatterns(
            records = records,
            analytics = analytics,
            habits = habits,
            notes = notes,
            appRules = appRules,
            existingVerified = existingPatterns,
            nowMs = now
        )
        repository.savePatterns(patterns)

        repository.memoryManager.consolidate(
            analytics = analytics,
            patterns = patterns,
            profile = profile,
            nowMs = now
        )

        val generatedInsights = buildEvidenceInsights(analytics, profile, patterns, usageGranted, now)
        repository.saveInsights(generatedInsights)

        return IntelligencePipelineResult(
            analytics = analytics,
            profile = profile,
            patterns = patterns,
            insights = repository.insightDao.getActive(15),
            usageAccessGranted = usageGranted
        )
    }

    private fun buildEvidenceInsights(
        analytics: AnalyticsSnapshot,
        profile: PersonalIntelligenceProfile,
        patterns: List<BehaviouralPatternEntity>,
        usageGranted: Boolean,
        nowMs: Long
    ): List<IntelligenceInsightEntity> {
        val insights = mutableListOf<IntelligenceInsightEntity>()

        // Core productivity insight
        val deltaText = if (analytics.scoreDeltaVsYesterday >= 0) {
            "+${analytics.scoreDeltaVsYesterday} pts above yesterday"
        } else {
            "${analytics.scoreDeltaVsYesterday} pts vs yesterday"
        }
        insights += IntelligenceInsightEntity(
            id = "ins_daily_score_${analytics.todayKey}",
            category = "PRODUCTIVITY",
            title = "Daily Productivity Score: ${analytics.dailyProductivityScore}/100 ($deltaText)",
            summary = if (analytics.todayFocusMinutes >= 25) {
                "You logged ${analytics.todayFocusMinutes}m of deep focus alongside ${analytics.habitsCompletedToday}/${analytics.habitsTotalToday} habits."
            } else {
                "Start one ${if (analytics.tasksOverdue > 0) "task-clearing " else ""}25–45m Deep-Work sprint during ${profile.peakProductiveWindow} to lift today's score."
            },
            evidence = "Focus ${analytics.todayFocusMinutes}m, Screen ${analytics.todayScreenMinutes}m, Habits ${analytics.habitsCompletedToday}/${analytics.habitsTotalToday}, Overdue ${analytics.tasksOverdue}",
            confidence = if (usageGranted) 0.88f else 0.68f,
            comparisonPeriod = "Today vs Yesterday",
            actionRecommendation = if (analytics.todayFocusMinutes < 25) "Start Deep-Work Sprint" else "",
            generatedAtMs = nowMs
        )

        // Convert high-signal behavioural patterns into actionable InsightCards
        patterns.take(5).forEach { pat ->
            val rec = when (pat.category) {
                "distraction_loop" -> "Review App Limits"
                "task_delay" -> "Clear Overdue Tasks"
                "sleep_recovery" -> "Adjust Sleep Shield"
                else -> ""
            }
            insights += IntelligenceInsightEntity(
                id = "ins_${pat.id}",
                category = pat.category.uppercase(Locale.US).replace('_', ' '),
                title = pat.title,
                summary = pat.summary,
                evidence = pat.evidence,
                confidence = pat.confidence,
                comparisonPeriod = pat.comparisonPeriod,
                actionRecommendation = rec,
                generatedAtMs = nowMs
            )
        }

        if (!usageGranted) {
            insights += IntelligenceInsightEntity(
                id = "ins_usage_perm",
                category = "TELEMETRY SCOPE",
                title = "Grant Usage Access for App Launch & Screen Patterns",
                summary = "Habit, task, focus, and diary intelligence are active. Granting Android Usage Access unlocks per-app launch frequency and hourly screen-time shifts.",
                evidence = "UsageStatsManager access is currently disabled by OS settings",
                confidence = 1.0f,
                comparisonPeriod = "Live OS permissions",
                actionRecommendation = "Enable Usage Access",
                generatedAtMs = nowMs
            )
        }

        return insights
    }

    suspend fun generateAiDailyBriefing(
        pipeline: IntelligencePipelineResult,
        persona: String
    ): Result<DailyBriefingResult> {
        val relevantMemories = repository.memoryManager.retrieveRelevantMemories(
            query = "daily score productivity screen time habits focus sleep",
            limit = 6
        )
        return geminiService.generateDailyBriefing(
            analytics = pipeline.analytics,
            profile = pipeline.profile,
            patterns = pipeline.patterns,
            retrievedMemories = relevantMemories,
            persona = persona
        )
    }

    suspend fun generatePeriodReport(
        periodTitle: String,
        pipeline: IntelligencePipelineResult
    ): Result<String> {
        val relevantMemories = repository.memoryManager.retrieveRelevantMemories(
            query = "$periodTitle comparison trend productivity screen time focus habits",
            limit = 6
        )
        return geminiService.generatePeriodReport(
            periodTitle = periodTitle,
            analytics = pipeline.analytics,
            profile = pipeline.profile,
            patterns = pipeline.patterns,
            retrievedMemories = relevantMemories
        )
    }

    suspend fun askIntelligenceQuestion(
        question: String,
        pipeline: IntelligencePipelineResult
    ): Result<IntelligenceQaAnswer> {
        val relevantMemories = repository.memoryManager.retrieveRelevantMemories(
            query = question,
            limit = 6
        )
        return geminiService.answerIntelligenceQuestion(
            question = question,
            analytics = pipeline.analytics,
            profile = pipeline.profile,
            patterns = pipeline.patterns,
            retrievedMemories = relevantMemories
        )
    }
}
