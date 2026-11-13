package com.example.data.intelligence

import android.content.Context
import com.example.data.local.ActivityRecordEntity
import com.example.data.local.AiMemoryEntity
import com.example.data.local.BehaviouralPatternEntity
import com.example.data.local.IntelligenceDatabase
import com.example.data.local.IntelligenceInsightEntity
import kotlinx.coroutines.flow.Flow

class IntelligenceRepository(context: Context) {

    private val db = IntelligenceDatabase.getInstance(context)
    val activityDao = db.activityRecordDao()
    val patternDao = db.patternDao()
    val insightDao = db.insightDao()
    val memoryDao = db.aiMemoryDao()
    val memoryManager = MemoryManager(memoryDao)

    fun observeRecentActivities(limit: Int = 120): Flow<List<ActivityRecordEntity>> =
        activityDao.observeRecent(limit)

    fun observePatterns(): Flow<List<BehaviouralPatternEntity>> =
        patternDao.observeAll()

    fun observeActiveInsights(): Flow<List<IntelligenceInsightEntity>> =
        insightDao.observeActive()

    fun observeMemories(): Flow<List<AiMemoryEntity>> =
        memoryDao.observeAll()

    suspend fun getRecordsWindow(days: Int, nowMs: Long = System.currentTimeMillis()): List<ActivityRecordEntity> {
        val sinceMs = nowMs - days.coerceIn(1, 365) * 86_400_000L
        return activityDao.getBetween(sinceMs, nowMs)
    }

    suspend fun savePatterns(patterns: List<BehaviouralPatternEntity>) {
        patternDao.clearUnverified()
        if (patterns.isNotEmpty()) {
            patternDao.upsertAll(patterns)
        }
    }

    suspend fun updatePatternFeedback(patternId: String, note: String) {
        val existing = patternDao.getAll().firstOrNull { it.id == patternId } ?: return
        val clean = note.trim()
        patternDao.upsert(
            existing.copy(
                userVerified = true,
                userNote = clean,
                summary = clean.ifBlank { existing.summary },
                confidence = 0.98f
            )
        )
    }

    suspend fun deletePattern(patternId: String) {
        patternDao.deleteById(patternId)
    }

    suspend fun saveInsights(insights: List<IntelligenceInsightEntity>) {
        if (insights.isNotEmpty()) {
            insightDao.upsertAll(insights)
        }
    }

    suspend fun dismissInsight(insightId: String) {
        insightDao.dismiss(insightId)
    }

    suspend fun enforceRetention(retentionDays: Int, nowMs: Long = System.currentTimeMillis()) {
        val cutoff = nowMs - retentionDays.coerceIn(7, 365) * 86_400_000L
        activityDao.deleteBefore(cutoff)
        insightDao.deleteBefore(cutoff)
        memoryDao.pruneAutoBefore(cutoff)
    }

    suspend fun clearAllIntelligenceData() {
        activityDao.clearAll()
        patternDao.clearAll()
        insightDao.clearAll()
        memoryDao.clearAll()
    }
}
