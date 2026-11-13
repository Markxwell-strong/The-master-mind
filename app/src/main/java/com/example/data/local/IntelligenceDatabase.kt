package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "activity_records")
data class ActivityRecordEntity(
    @PrimaryKey val id: String,
    val timestampMs: Long,
    val dateKey: String,
    val hourOfDay: Int,
    val sourceType: String, // app_usage, habit, task, focus_session, sleep, diary
    val itemKey: String,
    val label: String,
    val category: String, // productive, communication, social, video, gaming, browser, focus, wellness, sleep, task, diary, other
    val durationMinutes: Long = 0L,
    val launchCount: Int = 0,
    val completed: Boolean = false,
    val overdue: Boolean = false,
    val productivityWeight: Float = 0f, // -1f distraction .. +1f deep productivity
    val details: String = ""
)

@Entity(tableName = "behavioural_patterns")
data class BehaviouralPatternEntity(
    @PrimaryKey val id: String,
    val category: String, // peak_hours, frequent_apps, distraction_loop, habit_consistency, routine_shift, productivity_trend, task_delay, screen_time_shift, sleep_recovery
    val title: String,
    val summary: String,
    val evidence: String,
    val comparisonPeriod: String,
    val confidence: Float,
    val userVerified: Boolean = false,
    val userNote: String = "",
    val detectedAtMs: Long
)

@Entity(tableName = "intelligence_insights")
data class IntelligenceInsightEntity(
    @PrimaryKey val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val evidence: String,
    val confidence: Float,
    val comparisonPeriod: String,
    val actionRecommendation: String,
    val generatedAtMs: Long,
    val dismissed: Boolean = false
)

@Entity(tableName = "ai_memories")
data class AiMemoryEntity(
    @PrimaryKey val id: String,
    val tier: String, // short_term, long_term, analytical
    val topicKey: String,
    val summary: String,
    val evidenceCount: Int = 1,
    val confidence: Float = 0.7f,
    val userEdited: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

@Dao
interface ActivityRecordDao {
    @Query("SELECT * FROM activity_records ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int = 120): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM activity_records WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    fun observeSince(sinceMs: Long): Flow<List<ActivityRecordEntity>>

    @Query("SELECT * FROM activity_records WHERE timestampMs >= :sinceMs AND timestampMs <= :untilMs ORDER BY timestampMs ASC")
    suspend fun getBetween(sinceMs: Long, untilMs: Long): List<ActivityRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<ActivityRecordEntity>)

    @Query("DELETE FROM activity_records WHERE timestampMs < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long)

    @Query("DELETE FROM activity_records")
    suspend fun clearAll()
}

@Dao
interface PatternDao {
    @Query("SELECT * FROM behavioural_patterns ORDER BY confidence DESC, detectedAtMs DESC")
    fun observeAll(): Flow<List<BehaviouralPatternEntity>>

    @Query("SELECT * FROM behavioural_patterns ORDER BY confidence DESC, detectedAtMs DESC")
    suspend fun getAll(): List<BehaviouralPatternEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(patterns: List<BehaviouralPatternEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pattern: BehaviouralPatternEntity)

    @Query("DELETE FROM behavioural_patterns WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM behavioural_patterns WHERE userVerified = 0")
    suspend fun clearUnverified()

    @Query("DELETE FROM behavioural_patterns")
    suspend fun clearAll()
}

@Dao
interface InsightDao {
    @Query("SELECT * FROM intelligence_insights WHERE dismissed = 0 ORDER BY generatedAtMs DESC, confidence DESC")
    fun observeActive(): Flow<List<IntelligenceInsightEntity>>

    @Query("SELECT * FROM intelligence_insights WHERE dismissed = 0 ORDER BY generatedAtMs DESC, confidence DESC LIMIT :limit")
    suspend fun getActive(limit: Int = 20): List<IntelligenceInsightEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(insights: List<IntelligenceInsightEntity>)

    @Query("UPDATE intelligence_insights SET dismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: String)

    @Query("DELETE FROM intelligence_insights WHERE generatedAtMs < :cutoffMs")
    suspend fun deleteBefore(cutoffMs: Long)

    @Query("DELETE FROM intelligence_insights")
    suspend fun clearAll()
}

@Dao
interface AiMemoryDao {
    @Query("SELECT * FROM ai_memories ORDER BY tier ASC, updatedAtMs DESC")
    fun observeAll(): Flow<List<AiMemoryEntity>>

    @Query("SELECT * FROM ai_memories ORDER BY updatedAtMs DESC")
    suspend fun getAll(): List<AiMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memories: List<AiMemoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: AiMemoryEntity)

    @Query("DELETE FROM ai_memories WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM ai_memories WHERE userEdited = 0 AND updatedAtMs < :cutoffMs")
    suspend fun pruneAutoBefore(cutoffMs: Long)

    @Query("DELETE FROM ai_memories")
    suspend fun clearAll()
}

@Database(
    entities = [
        ActivityRecordEntity::class,
        BehaviouralPatternEntity::class,
        IntelligenceInsightEntity::class,
        AiMemoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class IntelligenceDatabase : RoomDatabase() {
    abstract fun activityRecordDao(): ActivityRecordDao
    abstract fun patternDao(): PatternDao
    abstract fun insightDao(): InsightDao
    abstract fun aiMemoryDao(): AiMemoryDao

    companion object {
        @Volatile
        private var INSTANCE: IntelligenceDatabase? = null

        fun getInstance(context: Context): IntelligenceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    IntelligenceDatabase::class.java,
                    "master_mind_intelligence.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
