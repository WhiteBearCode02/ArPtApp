package com.example.arptapp.data.remote

import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.data.history.WorkoutPayloadCodec
import com.example.arptapp.data.RepAnalysisCodec
import com.example.arptapp.model.RepAnalysis
import com.example.arptapp.model.RepRecord
import com.example.arptapp.domain.classifier.ExerciseType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Summary only; field names/types verified against the existing public schema (2026-11-01). */
@Serializable
data class RemoteExerciseSession(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("exercise_type") val exerciseType: String,
    @SerialName("total_reps") val totalReps: Int,
    @SerialName("average_score") val averageScore: Int,
    @SerialName("feedback_message") val feedbackMessage: String,
    @SerialName("duration_seconds") val durationSeconds: Long,
    @SerialName("burned_calories") val burnedCalories: Double,
    @SerialName("created_at") val createdAt: String,
    @SerialName("workout_date") val workoutDate: String? = null,
    @SerialName("exact_average_score") val exactAverageScore: Float? = null
)

/** Explicit DTO: newly added local fields cannot silently expand the network payload. */
@Serializable
data class RemoteWorkoutUpload(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("exercise_type") val exerciseType: String,
    @SerialName("total_reps") val totalReps: Int,
    @SerialName("average_score") val averageScore: Int,
    @SerialName("exact_average_score") val exactAverageScore: Float,
    @SerialName("feedback_message") val feedbackMessage: String,
    @SerialName("duration_seconds") val durationSeconds: Long,
    @SerialName("burned_calories") val burnedCalories: Double,
    @SerialName("created_at") val createdAt: String,
    @SerialName("workout_date") val workoutDate: String,
    @SerialName("report_json") val reportJson: String
)

@Serializable
data class RemoteRepRecord(
    @SerialName("session_id") val sessionId: String,
    @SerialName("rep_number") val repNumber: Int,
    @SerialName("max_angle") val maxAngle: Double,
    @SerialName("sway_x") val swayX: Float,
    val score: Float? = null,
    val detail: String = "",
    @SerialName("error_tags") val errorTags: List<String> = emptyList(),
    @SerialName("eccentric_duration_ms") val eccentricDurationMs: Long = 0,
    @SerialName("concentric_duration_ms") val concentricDurationMs: Long = 0,
    @SerialName("pose_score") val poseScore: Float? = null
) {
    fun localRep() = RepRecord(repNumber, maxAngle, swayX, errorTags, eccentricDurationMs, concentricDurationMs, poseScore)
}

object WorkoutRemoteMapper {
    fun summary(row: RemoteExerciseSession, ownerId: String): ExerciseRecord {
        require(row.userId == ownerId && row.id.isNotBlank())
        require(row.totalReps >= 0 && row.durationSeconds >= 0 && row.burnedCalories.isFinite() && row.burnedCalories >= 0)
        val score = row.exactAverageScore ?: row.averageScore.toFloat()
        require(score.isFinite() && score in 0f..100f)
        val date = row.workoutDate?.takeIf { it.isNotBlank() } ?: OffsetDateTime.parse(row.createdAt)
            .atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return ExerciseRecord(userId = ownerId, date = date, totalCount = row.totalReps,
            duration = row.durationSeconds, burnedCalories = row.burnedCalories,
            exerciseType = exercise(row.exerciseType)?.displayName ?: row.exerciseType,
            averageScore = score, feedbackMessage = row.feedbackMessage, sessionId = row.id,
            syncState = "SYNCED", cloudEligible = true, detailsCached = false, createdAt = row.createdAt)
    }
    fun upload(row: ExerciseRecord): RemoteWorkoutUpload {
        require(row.cloudEligible && row.userId.isNotBlank() && !row.sessionId.isNullOrBlank())
        require(row.createdAt.isNotBlank() && row.averageScore.isFinite())
        return RemoteWorkoutUpload(requireNotNull(row.sessionId), row.userId,
            exercise(row.exerciseType)?.name ?: row.exerciseType,
            row.totalCount, row.averageScore.toInt(), row.averageScore, row.feedbackMessage,
            row.duration, row.burnedCalories, row.createdAt, row.date, WorkoutPayloadCodec.analyses(row.scoresJson))
    }
    fun reps(row: ExerciseRecord): List<RemoteRepRecord> {
        val analyses = RepAnalysisCodec.decode(row.scoresJson).associateBy { it.repNumber }
        return WorkoutPayloadCodec.decodeReps(row.repRecordsJson).map { rep ->
            val analysis = analyses[rep.repNumber]
            RemoteRepRecord(requireNotNull(row.sessionId), rep.repNumber, rep.maxAngle, rep.swayX,
                analysis?.score, analysis?.detail.orEmpty(), rep.errorTags, rep.eccentricDurationMs,
                rep.concentricDurationMs, rep.poseScore)
        }
    }
    /** Old sessions have no structured envelope. Restore only data that actually exists. */
    fun legacyAnalyses(reps: List<RemoteRepRecord>): String = RepAnalysisCodec.encode(reps.mapNotNull { rep ->
        rep.score?.takeIf { it.isFinite() && it in 0f..100f }?.let { RepAnalysis(rep.repNumber, it, rep.detail) }
    })

    private fun exercise(value: String): ExerciseType? = ExerciseType.entries.firstOrNull {
        it.name == value || it.displayName == value
    }
}
