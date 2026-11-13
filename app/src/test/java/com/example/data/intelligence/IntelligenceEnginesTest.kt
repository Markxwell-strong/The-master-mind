package com.example.data.intelligence

import com.example.data.local.ActivityRecordEntity
import com.example.data.local.AiMemoryDao
import com.example.data.local.AiMemoryEntity
import com.example.data.local.BehaviouralPatternEntity
import com.example.data.model.AppLimitRule
import com.example.data.model.HabitItem
import com.example.data.model.NoteItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IntelligenceEnginesTest {

    private fun dateKeyOffset(nowMs: Long, daysAgo: Int): String {
        val ms = nowMs - daysAgo * 86_400_000L
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
    }

    @Test
    fun analyticsEngine_computesDailyScoreHourlyPeaksAndWeeklyComparison() {
        val nowMs = 1_760_000_000_000L
        val today = dateKeyOffset(nowMs, 0)
        val yesterday = dateKeyOffset(nowMs, 1)
        val priorWeekDay = dateKeyOffset(nowMs, 9)

        val records = listOf(
            ActivityRecordEntity(
                id = "r1",
                timestampMs = nowMs,
                dateKey = today,
                hourOfDay = 9,
                sourceType = "focus_session",
                itemKey = "deep_1",
                label = "Morning Deep Work",
                category = "focus",
                durationMinutes = 60L,
                completed = true,
                productivityWeight = 1.0f
            ),
            ActivityRecordEntity(
                id = "r2",
                timestampMs = nowMs,
                dateKey = today,
                hourOfDay = 10,
                sourceType = "habit",
                itemKey = "hab_1",
                label = "Read 20 pages",
                category = "wellness",
                completed = true,
                productivityWeight = 0.7f
            ),
            ActivityRecordEntity(
                id = "r3",
                timestampMs = nowMs,
                dateKey = today,
                hourOfDay = 21,
                sourceType = "app_usage",
                itemKey = "com.zhiliaoapp.musically",
                label = "TikTok",
                category = "social",
                durationMinutes = 25L,
                launchCount = 7,
                productivityWeight = -0.8f
            ),
            ActivityRecordEntity(
                id = "r4",
                timestampMs = nowMs - 86_400_000L,
                dateKey = yesterday,
                hourOfDay = 20,
                sourceType = "app_usage",
                itemKey = "com.zhiliaoapp.musically",
                label = "TikTok",
                category = "social",
                durationMinutes = 80L,
                launchCount = 14,
                productivityWeight = -0.8f
            ),
            ActivityRecordEntity(
                id = "r5",
                timestampMs = nowMs - 9 * 86_400_000L,
                dateKey = priorWeekDay,
                hourOfDay = 15,
                sourceType = "app_usage",
                itemKey = "com.google.android.youtube",
                label = "YouTube",
                category = "video",
                durationMinutes = 90L,
                launchCount = 8,
                productivityWeight = -0.6f
            )
        )

        val engine = AnalyticsEngine()
        val snap = engine.compute(records, nowMs)

        assertEquals(today, snap.todayKey)
        assertEquals(60L, snap.todayFocusMinutes)
        assertEquals(25L, snap.todayScreenMinutes)
        assertEquals(25L, snap.todayDistractionMinutes)
        assertEquals(7, snap.todayAppLaunches)
        assertEquals(1, snap.habitsCompletedToday)
        assertEquals(1, snap.habitsTotalToday)
        assertTrue("Expected today score > yesterday score", snap.dailyProductivityScore > (snap.dailyProductivityScore - snap.scoreDeltaVsYesterday))
        assertEquals(9, snap.peakProductiveHour)
        assertEquals(105L, snap.weekComparison.currentScreenMinutes)
        assertEquals(90L, snap.weekComparison.previousScreenMinutes)
        assertEquals(3, snap.observationDays)
    }

    @Test
    fun patternEngine_detectsPatternsAndPreservesUserVerifiedNotes() {
        val nowMs = 1_760_000_000_000L
        val today = dateKeyOffset(nowMs, 0)
        val records = listOf(
            ActivityRecordEntity(
                id = "f1",
                timestampMs = nowMs,
                dateKey = today,
                hourOfDay = 8,
                sourceType = "focus_session",
                itemKey = "s1",
                label = "Architecture Sprint",
                category = "focus",
                durationMinutes = 50L,
                completed = true,
                productivityWeight = 1.0f
            ),
            ActivityRecordEntity(
                id = "u1",
                timestampMs = nowMs,
                dateKey = today,
                hourOfDay = 23,
                sourceType = "app_usage",
                itemKey = "com.instagram.android",
                label = "Instagram",
                category = "social",
                durationMinutes = 35L,
                launchCount = 11,
                productivityWeight = -0.8f
            ),
            ActivityRecordEntity(
                id = "sl1",
                timestampMs = nowMs,
                dateKey = today,
                hourOfDay = 7,
                sourceType = "sleep",
                itemKey = "sleep",
                label = "Sleep Schedule",
                category = "sleep",
                durationMinutes = 420L,
                completed = true
            )
        )

        val analytics = AnalyticsEngine().compute(records, nowMs)
        val habits = listOf(
            HabitItem(id = "h1", title = "Hydrate", targetPerDay = 2, completedToday = 2, streakDays = 6),
            HabitItem(id = "h2", title = "Walk", targetPerDay = 1, completedToday = 0, streakDays = 1)
        )
        val notes = listOf(
            NoteItem(id = "n1", title = "Submit invoice", reminderAtMillis = nowMs - 60_000L, isCompleted = false)
        )
        val rules = listOf(
            AppLimitRule(id = "r1", packageName = "com.instagram.android", appName = "Instagram", dailyLimitMinutes = 20)
        )
        val existingVerified = listOf(
            BehaviouralPatternEntity(
                id = "pat_peak_hours",
                category = "peak_hours",
                title = "Peak Cognitive Window",
                summary = "User prefers 08:00 deep work blocks before meetings.",
                evidence = "",
                comparisonPeriod = "",
                confidence = 0.98f,
                userVerified = true,
                userNote = "User prefers 08:00 deep work blocks before meetings.",
                detectedAtMs = nowMs - 1000L
            )
        )

        val (profile, patterns) = PatternEngine().detectPatterns(
            records = records,
            analytics = analytics,
            habits = habits,
            notes = notes,
            appRules = rules,
            existingVerified = existingVerified,
            nowMs = nowMs
        )

        assertEquals(50, profile.habitConsistencyPercent)
        assertTrue(profile.primaryDistractionTrigger.contains("Instagram"))

        val categories = patterns.map { it.category }.toSet()
        assertTrue(categories.contains("peak_hours"))
        assertTrue(categories.contains("distraction_loop"))
        assertTrue(categories.contains("habit_consistency"))
        assertTrue(categories.contains("task_delay"))
        assertTrue(categories.contains("sleep_recovery"))

        val verifiedPeak = patterns.firstOrNull { it.id == "pat_peak_hours" }
        assertNotNull(verifiedPeak)
        assertTrue(verifiedPeak!!.userVerified)
        assertEquals("User prefers 08:00 deep work blocks before meetings.", verifiedPeak.summary)
    }

    @Test
    fun memoryManager_consolidatesAndRanksTargetedQueryRelevance() = runBlocking {
        val fakeDao = FakeAiMemoryDao()
        fakeDao.upsert(
            AiMemoryEntity(
                id = "mem_edited",
                tier = MemoryManager.TIER_LONG_TERM,
                topicKey = "morning focus reading",
                summary = "I always read technical books at 7am without notifications.",
                evidenceCount = 10,
                confidence = 1.0f,
                userEdited = true,
                createdAtMs = 100L,
                updatedAtMs = 100L
            )
        )

        val nowMs = 1_760_000_000_000L
        val analytics = AnalyticsEngine().compute(
            listOf(
                ActivityRecordEntity(
                    id = "a1",
                    timestampMs = nowMs,
                    dateKey = dateKeyOffset(nowMs, 0),
                    hourOfDay = 14,
                    sourceType = "app_usage",
                    itemKey = "com.zhiliaoapp.musically",
                    label = "TikTok",
                    category = "social",
                    durationMinutes = 40L,
                    launchCount = 9,
                    productivityWeight = -0.8f
                )
            ),
            nowMs
        )
        val (profile, patterns) = PatternEngine().detectPatterns(
            records = listOf(
                ActivityRecordEntity(
                    id = "a1",
                    timestampMs = nowMs,
                    dateKey = dateKeyOffset(nowMs, 0),
                    hourOfDay = 14,
                    sourceType = "app_usage",
                    itemKey = "com.zhiliaoapp.musically",
                    label = "TikTok",
                    category = "social",
                    durationMinutes = 40L,
                    launchCount = 9,
                    productivityWeight = -0.8f
                )
            ),
            analytics = analytics,
            habits = emptyList(),
            notes = emptyList(),
            appRules = emptyList(),
            nowMs = nowMs
        )

        val manager = MemoryManager(fakeDao)
        val all = manager.consolidate(analytics, patterns, profile, nowMs)
        assertTrue(all.any { it.id == "mem_edited" && it.userEdited })
        assertTrue(all.any { it.id == "mem_st_today_pulse" })
        assertTrue(all.any { it.id == "mem_an_weekly_delta" })

        val topForTikTok = manager.retrieveRelevantMemories("How much time am I wasting on TikTok?", limit = 2)
        assertTrue(topForTikTok.isNotEmpty())
        assertTrue(topForTikTok.first().id == "mem_lt_distraction")

        val topForMorning = manager.retrieveRelevantMemories("morning reading focus", limit = 2)
        assertEquals("mem_edited", topForMorning.first().id)
    }

    private class FakeAiMemoryDao : AiMemoryDao {
        private val store = LinkedHashMap<String, AiMemoryEntity>()
        private val flow = MutableStateFlow<List<AiMemoryEntity>>(emptyList())

        override fun observeAll(): Flow<List<AiMemoryEntity>> = flow

        override suspend fun getAll(): List<AiMemoryEntity> = store.values.toList()

        override suspend fun upsertAll(memories: List<AiMemoryEntity>) {
            memories.forEach { store[it.id] = it }
            flow.value = store.values.toList()
        }

        override suspend fun upsert(memory: AiMemoryEntity) {
            store[memory.id] = memory
            flow.value = store.values.toList()
        }

        override suspend fun deleteById(id: String) {
            store.remove(id)
            flow.value = store.values.toList()
        }

        override suspend fun pruneAutoBefore(cutoffMs: Long) {
            val keysToRemove = store.values.filter { !it.userEdited && it.updatedAtMs < cutoffMs }.map { it.id }
            keysToRemove.forEach { store.remove(it) }
            flow.value = store.values.toList()
        }

        override suspend fun clearAll() {
            store.clear()
            flow.value = emptyList()
        }
    }
}
