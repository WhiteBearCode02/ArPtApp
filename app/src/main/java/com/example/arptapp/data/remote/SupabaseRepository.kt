package com.example.arptapp.data.remote

import com.example.arptapp.model.RepRecord
import com.example.arptapp.model.RepAnalysis
import com.example.arptapp.model.SessionReport
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.UUID

class SupabaseRepository {
    suspend fun uploadSession(
        report: SessionReport,
        records: List<RepRecord>,
        analyses: List<RepAnalysis>,
        durationSeconds: Long,
        burnedCalories: Double
    ): Result<Unit> {
        val supabase = runCatching { SupabaseClientProvider.client }.getOrElse { error ->
            return Result.failure(error)
        }
        val userId = AuthSessionStore.current?.userId
            ?: return Result.failure(IllegalStateException("인증된 사용자가 없습니다."))
        val sessionId = UUID.randomUUID().toString()

        return runCatching {
            supabase.from("exercise_sessions").insert(
                SessionRow(
                    id = sessionId,
                    userId = userId,
                    exerciseType = report.exerciseType,
                    totalReps = report.totalReps,
                    averageScore = report.averageScore,
                    feedbackMessage = report.feedbackMessage,
                    durationSeconds = durationSeconds.coerceAtLeast(0L),
                    burnedCalories = burnedCalories.coerceAtLeast(0.0)
                )
            )
            if (records.isNotEmpty()) {
                val analysesByRep = analyses.associateBy { it.repNumber }
                supabase.from("exercise_rep_records").insert(
                    records.map { record ->
                        val analysis = analysesByRep[record.repNumber]
                        RepRow(
                            sessionId = sessionId,
                            repNumber = record.repNumber,
                            maxAngle = record.maxAngle,
                            swayX = record.swayX,
                            score = analysis?.score,
                            detail = analysis?.detail.orEmpty(),
                            errorTags = record.errorTags,
                            eccentricDurationMs = record.eccentricDurationMs,
                            concentricDurationMs = record.concentricDurationMs,
                            poseScore = record.poseScore
                        )
                    }
                )
            }
        }
    }

    @Serializable
    private data class SessionRow(
        val id: String,
        @SerialName("user_id")
        val userId: String,
        @SerialName("exercise_type")
        val exerciseType: String,
        @SerialName("total_reps")
        val totalReps: Int,
        @SerialName("average_score")
        val averageScore: Int,
        @SerialName("feedback_message")
        val feedbackMessage: String,
        @SerialName("duration_seconds")
        val durationSeconds: Long,
        @SerialName("burned_calories")
        val burnedCalories: Double
    )

    @Serializable
    private data class RepRow(
        @SerialName("session_id")
        val sessionId: String,
        @SerialName("rep_number")
        val repNumber: Int,
        @SerialName("max_angle")
        val maxAngle: Double,
        @SerialName("sway_x")
        val swayX: Float,
        val score: Float?,
        val detail: String,
        @SerialName("error_tags")
        val errorTags: List<String>,
        @SerialName("eccentric_duration_ms")
        val eccentricDurationMs: Long,
        @SerialName("concentric_duration_ms")
        val concentricDurationMs: Long,
        @SerialName("pose_score")
        val poseScore: Float?
    )
}
