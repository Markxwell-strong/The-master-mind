package com.example.ui.viewmodel

import com.example.data.ai.DailyBriefingResult
import com.example.data.ai.IntelligenceQaAnswer
import com.example.data.intelligence.AnalyticsSnapshot
import com.example.data.intelligence.PeriodComparison
import com.example.data.intelligence.PersonalIntelligenceProfile
import com.example.data.local.ActivityRecordEntity
import com.example.data.local.AiMemoryEntity
import com.example.data.local.BehaviouralPatternEntity
import com.example.data.local.IntelligenceInsightEntity

data class IntelligenceQaMessage(
    val id: String,
    val question: String,
    val answer: String,
    val citedEvidence: List<String> = emptyList(),
    val isError: Boolean = false
)

data class AppNotificationItem(
    val id: String,
    val title: String,
    val detail: String,
    val severity: String // info, warning, urgent
)

data class IntelligenceUiState(
    val isRefreshing: Boolean = false,
    val monitoringEnabled: Boolean = true,
    val retentionDays: Int = 30,
    val usageAccessGranted: Boolean = false,
    val analytics: AnalyticsSnapshot = emptyAnalyticsSnapshot(),
    val profile: PersonalIntelligenceProfile = PersonalIntelligenceProfile(
        peakProductiveWindow = "Needs data",
        peakActiveWindow = "Needs data",
        primaryFocusAnchor = "No sprint yet",
        primaryDistractionTrigger = "None detected",
        habitConsistencyPercent = 0,
        averageDailyScreenMinutes = 0L,
        sleepAlignmentLabel = "7.5h sleep target",
        productivityMomentumLabel = "Baseline ready",
        observationDays = 0
    ),
    val patterns: List<BehaviouralPatternEntity> = emptyList(),
    val insights: List<IntelligenceInsightEntity> = emptyList(),
    val memories: List<AiMemoryEntity> = emptyList(),
    val recentActivities: List<ActivityRecordEntity> = emptyList(),
    val briefing: DailyBriefingResult? = null,
    val briefingLoading: Boolean = false,
    val briefingError: String? = null,
    val periodReport: String? = null,
    val periodReportLoading: Boolean = false,
    val qaMessages: List<IntelligenceQaMessage> = emptyList(),
    val qaLoading: Boolean = false
)

fun emptyAnalyticsSnapshot(): AnalyticsSnapshot = AnalyticsSnapshot(
    todayKey = "",
    dailyProductivityScore = 50,
    scoreDeltaVsYesterday = 0,
    todayFocusMinutes = 0L,
    todayScreenMinutes = 0L,
    todayProductiveAppMinutes = 0L,
    todayDistractionMinutes = 0L,
    todayAppLaunches = 0,
    habitsCompletedToday = 0,
    habitsTotalToday = 0,
    tasksPending = 0,
    tasksOverdue = 0,
    tasksCompleted = 0,
    estimatedSleepHours = 7.5,
    peakActiveHour = null,
    peakProductiveHour = null,
    hourlyActivityMinutes = emptyMap(),
    hourlyProductivityScore = emptyMap(),
    categoryBreakdown = emptyList(),
    topLaunchedApps = emptyList(),
    weeklySeries = emptyList(),
    weekComparison = PeriodComparison(
        currentLabel = "This 7d",
        previousLabel = "Prior 7d",
        currentAvgScore = 0,
        previousAvgScore = 0,
        scoreDelta = 0,
        currentScreenMinutes = 0L,
        previousScreenMinutes = 0L,
        screenDeltaPercent = 0,
        currentFocusMinutes = 0L,
        previousFocusMinutes = 0L,
        focusDeltaPercent = 0,
        currentHabitsDone = 0,
        previousHabitsDone = 0
    ),
    observationDays = 0
)
