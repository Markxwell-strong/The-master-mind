package com.example.data.ai

import android.util.Log
import com.example.data.intelligence.AnalyticsSnapshot
import com.example.data.intelligence.PersonalIntelligenceProfile
import com.example.data.local.AiMemoryEntity
import com.example.data.local.BehaviouralPatternEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

data class DailyBriefingResult(
    val headline: String,
    val summary: String,
    val routineRecommendations: List<String>,
    val confidenceLabel: String
)

data class IntelligenceQaAnswer(
    val answer: String,
    val citedEvidence: List<String>
)

class GeminiService {

    private val briefingSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(description = "Crisp 4-8 word daily intelligence headline"),
            "summary" to Schema.string(description = "2-3 sentence evidence-grounded interpretation of today's productivity, screen time, habits, and sleep"),
            "routineRecommendations" to Schema.array(
                items = Schema.string(),
                description = "3 concrete, personalized schedule or focus adjustments grounded in observed peak hours and distractions"
            ),
            "confidenceLabel" to Schema.string(description = "Brief note on evidence basis, e.g., Based on 7 days of usage & habit data")
        )
    )

    private val briefingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = briefingSchema
            }
        )
    }

    private val qaModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are THE MASTER MIND Intelligence Engine powered by Gemini. " +
                        "Answer questions strictly from the provided verified telemetry, detected behavioural patterns, historical comparisons, and retrieved AI memories. " +
                        "Cite exact numbers, hours, and comparison windows. Never invent observations not supported by evidence. Keep answers structured and actionable."
                )
            }
        )
    }

    suspend fun generateDailyBriefing(
        analytics: AnalyticsSnapshot,
        profile: PersonalIntelligenceProfile,
        patterns: List<BehaviouralPatternEntity>,
        retrievedMemories: List<AiMemoryEntity>,
        persona: String
    ): Result<DailyBriefingResult> {
        return try {
            val evidencePacket = buildEvidencePacket(analytics, profile, patterns, retrievedMemories)
            val prompt = """
                Role: Evidence-based Productivity Intelligence Coach (style: $persona).
                Interpret the verified behavioural evidence below to produce a Daily Intelligence Briefing.
                Rules:
                - Reference actual observed numbers and windows only.
                - Highlight one win, one risk/friction pattern, and 3 tailored routine optimizations.
                
                VERIFIED TELEMETRY & MEMORY PACKET:
                $evidencePacket
            """.trimIndent()

            val response = withTimeout(55_000L) {
                briefingModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("Empty briefing response")
            val json = JSONObject(raw)
            val recsJson = json.optJSONArray("routineRecommendations")
            val recs = if (recsJson != null) {
                (0 until recsJson.length()).mapNotNull { idx ->
                    recsJson.optString(idx).takeIf { it.isNotBlank() }
                }
            } else emptyList()

            Result.success(
                DailyBriefingResult(
                    headline = json.optString("headline", "Daily Intelligence Briefing"),
                    summary = json.optString("summary", "Telemetry updated across focus, habits, and screen time."),
                    routineRecommendations = recs,
                    confidenceLabel = json.optString(
                        "confidenceLabel",
                        "Based on ${analytics.observationDays} day(s) of observed signals"
                    )
                )
            )
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Daily briefing timed out. Showing local evidence insights."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "generateDailyBriefing failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Gemini briefing unavailable."))
        }
    }

    suspend fun generatePeriodReport(
        periodTitle: String,
        analytics: AnalyticsSnapshot,
        profile: PersonalIntelligenceProfile,
        patterns: List<BehaviouralPatternEntity>,
        retrievedMemories: List<AiMemoryEntity>
    ): Result<String> {
        return try {
            val evidencePacket = buildEvidencePacket(analytics, profile, patterns, retrievedMemories)
            val prompt = """
                Generate a concise $periodTitle Performance & Behavioural Intelligence Report.
                Compare current vs previous performance using the exact metrics provided, explain what drove productivity shifts, and give 3 concrete focus/routine optimizations.
                
                $evidencePacket
            """.trimIndent()

            val resp = withTimeout(55_000L) {
                qaModel.generateContent(prompt)
            }
            val text = resp.text?.trim().orEmpty()
            if (text.isEmpty()) throw IllegalStateException("Empty period report")
            Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "generatePeriodReport failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Failed to generate period report."))
        }
    }

    suspend fun answerIntelligenceQuestion(
        question: String,
        analytics: AnalyticsSnapshot,
        profile: PersonalIntelligenceProfile,
        patterns: List<BehaviouralPatternEntity>,
        retrievedMemories: List<AiMemoryEntity>
    ): Result<IntelligenceQaAnswer> {
        return try {
            val evidencePacket = buildEvidencePacket(analytics, profile, patterns, retrievedMemories)
            val prompt = """
                User Question: "$question"
                
                Answer clearly using the structured evidence and retrieved memories below. Compare current and historical periods where relevant.
                
                $evidencePacket
            """.trimIndent()

            val resp = withTimeout(55_000L) {
                qaModel.generateContent(prompt)
            }
            val text = resp.text?.trim() ?: throw IllegalStateException("Empty answer")
            val cited = patterns.take(3).map { "${it.title} (${(it.confidence * 100).toInt()}%)" }
            Result.success(IntelligenceQaAnswer(answer = text, citedEvidence = cited))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "answerIntelligenceQuestion failed", e)
            Result.failure(Exception(e.localizedMessage ?: "Intelligence Q&A failed."))
        }
    }

    fun buildEvidencePacket(
        analytics: AnalyticsSnapshot,
        profile: PersonalIntelligenceProfile,
        patterns: List<BehaviouralPatternEntity>,
        retrievedMemories: List<AiMemoryEntity>
    ): String {
        val comp = analytics.weekComparison
        val topCategories = analytics.categoryBreakdown.take(4)
            .joinToString(", ") { "${it.category}: ${it.minutes}m (${it.launches}x)" }
            .ifBlank { "No app category stats granted" }

        val patternLines = patterns.take(7).joinToString("\n") { p ->
            "- [${p.category}] ${p.title} (${(p.confidence * 100).toInt()}% conf): ${p.summary} | Evidence: ${p.evidence} | Window: ${p.comparisonPeriod}"
        }.ifBlank { "- Insufficient patterns detected yet." }

        val memoryLines = retrievedMemories.joinToString("\n") { m ->
            "- [${m.tier}] ${m.summary}"
        }.ifBlank { "- No stored memories matched." }

        return """
            TODAY (${analytics.todayKey}):
            - Productivity Score: ${analytics.dailyProductivityScore}/100 (delta vs yesterday: ${analytics.scoreDeltaVsYesterday})
            - Focus Sprints: ${analytics.todayFocusMinutes}m | Screen Time: ${analytics.todayScreenMinutes}m across ${analytics.todayAppLaunches} app launches
            - Habits Done: ${analytics.habitsCompletedToday}/${analytics.habitsTotalToday} | Tasks Pending: ${analytics.tasksPending} (Overdue: ${analytics.tasksOverdue})
            - Sleep Allocation: ${String.format(Locale.US, "%.1f", analytics.estimatedSleepHours)}h
            - Category Breakdown: $topCategories

            PROFILE & HISTORICAL COMPARISON (${comp.currentLabel} vs ${comp.previousLabel}):
            - Peak Productive Window: ${profile.peakProductiveWindow} | Peak Active: ${profile.peakActiveWindow}
            - Weekly Avg Score: ${comp.currentAvgScore} vs ${comp.previousAvgScore} (delta ${comp.scoreDelta})
            - Weekly Screen Time: ${comp.currentScreenMinutes}m vs ${comp.previousScreenMinutes}m (${comp.screenDeltaPercent}%)
            - Weekly Focus Time: ${comp.currentFocusMinutes}m vs ${comp.previousFocusMinutes}m (${comp.focusDeltaPercent}%)

            DETECTED BEHAVIOURAL PATTERNS:
            $patternLines

            RETRIEVED AI MEMORIES:
            $memoryLines
        """.trimIndent()
    }

    private companion object {
        const val TAG = "GeminiService"
    }
}
