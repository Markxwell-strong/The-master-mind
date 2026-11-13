package com.example.data.intelligence

import com.example.data.local.AiMemoryDao
import com.example.data.local.AiMemoryEntity
import com.example.data.local.BehaviouralPatternEntity
import java.util.Locale

class MemoryManager(private val memoryDao: AiMemoryDao) {

    companion object {
        const val TIER_SHORT_TERM = "short_term"
        const val TIER_LONG_TERM = "long_term"
        const val TIER_ANALYTICAL = "analytical"
    }

    suspend fun consolidate(
        analytics: AnalyticsSnapshot,
        patterns: List<BehaviouralPatternEntity>,
        profile: PersonalIntelligenceProfile,
        nowMs: Long = System.currentTimeMillis()
    ): List<AiMemoryEntity> {
        val existing = memoryDao.getAll().associateBy { it.id }
        val candidates = mutableListOf<AiMemoryEntity>()

        // Short-term memory: today's operational pulse & active blockers
        candidates += AiMemoryEntity(
            id = "mem_st_today_pulse",
            tier = TIER_SHORT_TERM,
            topicKey = "daily_score screen_time habits focus today",
            summary = "Today (${analytics.todayKey}): Productivity ${analytics.dailyProductivityScore}/100, Focus ${analytics.todayFocusMinutes}m, Screen ${analytics.todayScreenMinutes}m (${analytics.todayAppLaunches} launches), Habits ${analytics.habitsCompletedToday}/${analytics.habitsTotalToday}, Overdue tasks: ${analytics.tasksOverdue}.",
            evidenceCount = (analytics.todayAppLaunches + analytics.habitsTotalToday + 1),
            confidence = 0.92f,
            createdAtMs = existing["mem_st_today_pulse"]?.createdAtMs ?: nowMs,
            updatedAtMs = nowMs
        )

        patterns.firstOrNull { it.category == "task_delay" }?.let { delayPat ->
            candidates += AiMemoryEntity(
                id = "mem_st_overdue",
                tier = TIER_SHORT_TERM,
                topicKey = "overdue tasks reminders backlog delay",
                summary = "${delayPat.title}: ${delayPat.evidence}",
                evidenceCount = analytics.tasksOverdue.coerceAtLeast(1),
                confidence = delayPat.confidence,
                createdAtMs = existing["mem_st_overdue"]?.createdAtMs ?: nowMs,
                updatedAtMs = nowMs
            )
        }

        // Long-term memory: stable preferences, peak hours, distractions, habit consistency
        patterns.firstOrNull { it.category == "peak_hours" }?.let { peak ->
            candidates += AiMemoryEntity(
                id = "mem_lt_peak_hours",
                tier = TIER_LONG_TERM,
                topicKey = "peak hours schedule productive morning afternoon evening routine",
                summary = "${peak.title} — ${peak.summary}",
                evidenceCount = analytics.observationDays.coerceAtLeast(1),
                confidence = peak.confidence,
                createdAtMs = existing["mem_lt_peak_hours"]?.createdAtMs ?: nowMs,
                updatedAtMs = nowMs
            )
        }

        patterns.firstOrNull { it.category == "distraction_loop" }?.let { dist ->
            candidates += AiMemoryEntity(
                id = "mem_lt_distraction",
                tier = TIER_LONG_TERM,
                topicKey = "distraction scrolling tiktok instagram youtube app usage limit",
                summary = "${dist.title}: ${dist.evidence}",
                evidenceCount = analytics.observationDays.coerceAtLeast(1),
                confidence = dist.confidence,
                createdAtMs = existing["mem_lt_distraction"]?.createdAtMs ?: nowMs,
                updatedAtMs = nowMs
            )
        }

        patterns.firstOrNull { it.category == "habit_consistency" }?.let { hab ->
            candidates += AiMemoryEntity(
                id = "mem_lt_habits",
                tier = TIER_LONG_TERM,
                topicKey = "habit streak consistency wellness routine",
                summary = "${hab.title} — ${hab.evidence}",
                evidenceCount = analytics.habitsTotalToday.coerceAtLeast(1),
                confidence = hab.confidence,
                createdAtMs = existing["mem_lt_habits"]?.createdAtMs ?: nowMs,
                updatedAtMs = nowMs
            )
        }

        // Analytical memory: historical & weekly comparisons
        val comp = analytics.weekComparison
        candidates += AiMemoryEntity(
            id = "mem_an_weekly_delta",
            tier = TIER_ANALYTICAL,
            topicKey = "weekly trend comparison productivity growth screen time focus history",
            summary = "${comp.currentLabel} vs ${comp.previousLabel}: Avg Score ${comp.currentAvgScore} (${if (comp.scoreDelta >= 0) "+${comp.scoreDelta}" else comp.scoreDelta}), Focus ${comp.currentFocusMinutes}m vs ${comp.previousFocusMinutes}m (${comp.focusDeltaPercent}%), Screen ${comp.currentScreenMinutes}m vs ${comp.previousScreenMinutes}m (${comp.screenDeltaPercent}%).",
            evidenceCount = analytics.observationDays.coerceAtLeast(1),
            confidence = 0.85f,
            createdAtMs = existing["mem_an_weekly_delta"]?.createdAtMs ?: nowMs,
            updatedAtMs = nowMs
        )

        candidates += AiMemoryEntity(
            id = "mem_an_sleep_align",
            tier = TIER_ANALYTICAL,
            topicKey = "sleep bedtime wake late night circadian recovery",
            summary = "Sleep profile: ${profile.sleepAlignmentLabel}. Momentum: ${profile.productivityMomentumLabel}. Primary distraction trigger: ${profile.primaryDistractionTrigger}.",
            evidenceCount = analytics.observationDays.coerceAtLeast(1),
            confidence = 0.80f,
            createdAtMs = existing["mem_an_sleep_align"]?.createdAtMs ?: nowMs,
            updatedAtMs = nowMs
        )

        // Never overwrite memories explicitly edited by the user
        val toWrite = candidates.filter { cand ->
            existing[cand.id]?.userEdited != true
        }
        if (toWrite.isNotEmpty()) {
            memoryDao.upsertAll(toWrite)
        }
        return memoryDao.getAll()
    }

    /**
     * Targeted, query-relevant memory retrieval so Gemini never receives unfiltered raw dumps.
     */
    suspend fun retrieveRelevantMemories(query: String, limit: Int = 5): List<AiMemoryEntity> {
        val all = memoryDao.getAll()
        return rankRelevantMemories(all, query, limit)
    }

    fun rankRelevantMemories(
        allMemories: List<AiMemoryEntity>,
        query: String,
        limit: Int = 5
    ): List<AiMemoryEntity> {
        if (allMemories.isEmpty()) return emptyList()
        val tokens = query.lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

        return allMemories.sortedByDescending { mem ->
            val corpusWords = "${mem.topicKey} ${mem.summary} ${mem.tier}"
                .lowercase(Locale.US)
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotBlank() }
                .toSet()
            val overlap = tokens.count { tok ->
                corpusWords.any { word -> word == tok || (tok.length >= 4 && word.startsWith(tok)) }
            } * 3.0
            val tierWeight = when (mem.tier) {
                TIER_ANALYTICAL -> 1.4
                TIER_LONG_TERM -> 1.2
                else -> 1.0
            }
            val userBoost = if (mem.userEdited) 1.5 else 0.0
            overlap + (mem.confidence * 2.0) + tierWeight + userBoost
        }.take(limit)
    }

    suspend fun updateMemory(memoryId: String, updatedSummary: String) {
        val clean = updatedSummary.trim()
        if (clean.isEmpty()) return
        val existing = memoryDao.getAll().firstOrNull { it.id == memoryId } ?: return
        memoryDao.upsert(
            existing.copy(
                summary = clean,
                userEdited = true,
                confidence = 1.0f,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMemory(memoryId: String) {
        memoryDao.deleteById(memoryId)
    }

    suspend fun clearAllMemories() {
        memoryDao.clearAll()
    }
}
