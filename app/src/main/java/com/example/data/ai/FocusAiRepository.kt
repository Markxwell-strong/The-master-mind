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

import java.util.Locale

data class DiaryEvaluationResult(
    val score: Long,
    val feedback: String,
    val recommendations: List<String>
)

data class SupportTriageResult(
    val diagnosis: String,
    val resolvedLikely: Boolean,
    val developerSummary: String,
    val recommendedCategory: String
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

    private val supportTriageSchema = Schema.obj(
        properties = mapOf(
            "diagnosis" to Schema.string(description = "Clear step-by-step troubleshooting guidance tailored to THE MASTER MIND app"),
            "resolvedLikely" to Schema.boolean(description = "True if user self-service steps likely fix the issue; false if developer/admin attention is needed"),
            "developerSummary" to Schema.string(description = "Concise 2-line diagnostic brief ready to forward to the developer/admin"),
            "recommendedCategory" to Schema.enumeration(
                values = listOf("support", "permissions", "sync", "ai", "bug", "other"),
                description = "Normalized support category"
            )
        )
    )

    private val supportTriageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = supportTriageSchema
            }
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
            val hoursStr = String.format(Locale.US, "%.1f", onlineHours)
            val hasActivities = activitiesText.trim().length >= 5
            val prompt = if (hasActivities) {
                """
                You are THE MASTER MIND AI Buddy (coaching style: $persona).
                Evaluate the user's daily diary entry and provide constructive feedback on productivity and goal attainment.
                - User Activities Logged Today: "$activitiesText"
                - Screen/Online Hours Today: $hoursStr hours
                - Top Apps Used: $topAppSummary
                - Habits Completed: $habitsCompleted / $habitsTotal
                - Self-reported Mood: $moodScore/5, Energy: $energyScore/5
                
                Return a JSON object with productivityScore (0-100), constructiveFeedback, and 3 concrete next-step recommendations.
                """.trimIndent()
            } else {
                """
                You are THE MASTER MIND AI Buddy (coaching style: $persona).
                The user has NOT recorded any activities in their daily diary yet today!
                However, their real-time online/screen-time telemetry shows $hoursStr hours online today ($topAppSummary).
                Habits completed: $habitsCompleted / $habitsTotal. Mood: $moodScore/5, Energy: $energyScore/5.
                
                Because no activities are recorded, score baseline momentum honestly and immediately recommend 4-5 tailored offline & high-leverage activities calibrated to their $hoursStr online hours to help them break passive scrolling and regain momentum.
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
            Result.failure(Exception(formatAiError(e, "Gemini AI evaluation failed.")))
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

        // Initialize model with explicit systemInstruction so chat history remains strictly user/model alternating.
        val chatModel = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(systemIntro) }
        )

        val normalizedHistory = buildNormalizedHistory(historyPairs)
        val chat = chatModel.startChat(history = normalizedHistory)
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
            throw Exception(formatAiError(e, "AI Buddy failed to respond."))
        }
    }

    private fun buildNormalizedHistory(rawPairs: List<Pair<String, String>>): List<Content> {
        val merged = mutableListOf<Pair<String, String>>()
        for ((role, rawText) in rawPairs.takeLast(12)) {
            val cleanRole = if (role == "user") "user" else "model"
            val cleanText = rawText.trim()
            if (cleanText.isEmpty()) continue
            // Skip leading model greetings before the first user turn
            if (merged.isEmpty() && cleanRole != "user") continue
            val last = merged.lastOrNull()
            if (last != null && last.first == cleanRole) {
                merged[merged.lastIndex] = cleanRole to "${last.second}\n\n$cleanText"
            } else {
                merged.add(cleanRole to cleanText)
            }
        }
        // chat.sendMessageStream(userMessage) supplies the next user turn, so history must end on "model"
        while (merged.isNotEmpty() && merged.last().first != "model") {
            merged.removeAt(merged.lastIndex)
        }
        return merged.map { (role, msg) -> content(role) { text(msg) } }
    }

    suspend fun troubleshootSupportIssue(
        issueText: String,
        category: String,
        appDiagnostics: String
    ): Result<SupportTriageResult> {
        return try {
            val prompt = """
                You are the Gemini Support Engineer for THE MASTER MIND Android app (Developer WhatsApp: +254707934241, Admin Email: cleandemon5@gmail.com).
                A user submitted a support request:
                - Category: $category
                - User Issue: "$issueText"
                - Live Device & Account Diagnostics: $appDiagnostics
                
                Try to help the user resolve the issue immediately with clear, numbered steps (e.g., granting Usage Access / Accessibility / Notifications / Exact Alarms / DND permissions, checking sync, or customizing dashboards).
                If the issue is a crash/bug or requires developer intervention, set resolvedLikely = false so the app escalates it to the Admin Inbox & WhatsApp.
                Return valid JSON matching the schema.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                supportTriageModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Gemini returned an empty response.")
            val json = JSONObject(rawJson)
            val diagnosis = json.optString("diagnosis", "Please review app permissions in Settings and escalate to the developer if needed.")
            val resolvedLikely = json.optBoolean("resolvedLikely", false)
            val devSummary = json.optString("developerSummary", issueText.take(240))
            val recCat = json.optString("recommendedCategory", category)
            Result.success(
                SupportTriageResult(
                    diagnosis = diagnosis,
                    resolvedLikely = resolvedLikely,
                    developerSummary = devSummary,
                    recommendedCategory = recCat
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Support triage timed out", e)
            Result.failure(Exception("Gemini support assistant timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Support triage failed", e)
            Result.failure(Exception(formatAiError(e, "Gemini support request failed.")))
        }
    }

    private fun formatAiError(error: Exception, fallback: String): String {
        val raw = error.localizedMessage ?: error.message ?: fallback
        return when {
            raw.contains("AppCheck", ignoreCase = true) || raw.contains("403") ->
                "Gemini AI App Check verification failed ($raw). Launch from AI Studio or check network connection."
            raw.contains("Unable to resolve host", ignoreCase = true) || raw.contains("network", ignoreCase = true) ->
                "No internet connection to reach Gemini AI. Check your connection and try again."
            else -> raw
        }
    }

    private companion object {
        const val TAG = "FocusAiRepo"
    }
}
