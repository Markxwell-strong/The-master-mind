package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class DiaryEvaluationResult(
    val score: Long,
    val feedback: String,
    val recommendations: List<String>
)

class FocusAiRepository {

    private val evaluationSchema = Schema.obj(
        properties = mapOf(
            "productivityScore" to Schema.integer(description = "Goal attainment & productivity score from 0 to 100"),
            "constructiveFeedback" to Schema.string(description = "Concise, actionable, encouraging evaluation of the day's activities, screen time, and habits"),
            "recommendations" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 specific recommended activities tailored to online hours and goals"
            )
        )
    )

    private val evaluationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = evaluationSchema
            }
        )
    }

    private val buddyModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    suspend fun evaluateDayOrRecommend(
        activitiesText: String,
        onlineHours: Double,
        habitsCompleted: Int,
        habitsTotal: Int,
        moodScore: Int,
        energyScore: Int,
        topAppSummary: String,
        persona: String
    ): Result<DiaryEvaluationResult> {
        return try {
            val hasActivities = activitiesText.trim().length >= 5
            val prompt = if (hasActivities) {
                """
                You are THE MASTER MIND AI Buddy (coaching style: $persona).
                Evaluate the user's daily diary entry and provide constructive feedback on productivity and goal attainment.
                - User Activities Logged Today: "$activitiesText"
                - Screen/Online Hours Today: ${String.format("%.1f", onlineHours)} hours
                - Top Apps Used: $topAppSummary
                - Habits Completed: $habitsCompleted / $habitsTotal
                - Self-reported Mood: $moodScore/5, Energy: $energyScore/5
                
                Return a JSON object with productivityScore (0-100), constructiveFeedback, and 3 concrete next-step recommendations.
                """.trimIndent()
            } else {
                """
                You are THE MASTER MIND AI Buddy (coaching style: $persona).
                The user has NOT recorded any activities in their daily diary yet today!
                However, their real-time online/screen-time telemetry shows ${String.format("%.1f", onlineHours)} hours online today ($topAppSummary).
                Habits completed: $habitsCompleted / $habitsTotal. Mood: $moodScore/5, Energy: $energyScore/5.
                
                Because no activities are recorded, score baseline momentum honestly and immediately recommend 4-5 tailored offline & high-leverage activities calibrated to their ${String.format("%.1f", onlineHours)} online hours to help them break passive scrolling and regain momentum.
                """.trimIndent()
            }

            val response = withTimeout(60_000L) {
                evaluationModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI returned empty response.")
            val json = JSONObject(rawJson)
            val score = json.optLong("productivityScore", 70L).coerceIn(0L, 100L)
            val feedback = json.optString("constructiveFeedback", "Keep building steady focus blocks.")
            val recArray = json.optJSONArray("recommendations")
            val recs = if (recArray != null) {
                (0 until recArray.length()).mapNotNull { idx ->
                    recArray.optString(idx).takeIf { it.isNotBlank() }
                }
            } else {
                emptyList()
            }
            Result.success(DiaryEvaluationResult(score = score, feedback = feedback, recommendations = recs))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI evaluation timed out", e)
            Result.failure(Exception("AI evaluation timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI evaluation failed", e)
            Result.failure(e)
        }
    }

    fun streamBuddyReply(
        userMessage: String,
        historyPairs: List<Pair<String, String>>,
        contextSummary: String,
        persona: String
    ): Flow<String> = flow {
        val systemIntro = """
            You are THE MASTER MIND AI Buddy powered by Gemini (active persona: $persona).
            Live user context:
            $contextSummary
            Be concise, practical, encouraging, and directly helpful with habits, anti-doomscrolling, sleep hygiene, deep work, business inventory/financials, or daily planning.
        """.trimIndent()

        val history = mutableListOf<Content>(
            content("user") { text(systemIntro) },
            content("model") { text("Ready! I have your live THE MASTER MIND context. How can I help you right now?") }
        )
        historyPairs.takeLast(8).forEach { (role, msg) ->
            history.add(content(role) { text(msg) })
        }

        val chat = buddyModel.startChat(history = history)
        val buffer = StringBuilder()
        try {
            withTimeout(60_000L) {
                chat.sendMessageStream(userMessage).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        buffer.append(piece)
                        emit(buffer.toString())
                    }
                }
            }
            if (buffer.isBlank()) {
                throw IllegalStateException("AI Buddy returned an empty response.")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI Buddy stream timed out", e)
            throw Exception("AI Buddy timed out after 60 seconds. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI Buddy stream failed", e)
            throw e
        }
    }

    private companion object {
        const val TAG = "FocusAiRepo"
    }
}
