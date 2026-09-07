package com.example.arptapp.data.remote

import com.example.arptapp.BuildConfig
import com.example.arptapp.model.RepRecord
import com.example.arptapp.model.SessionReport
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.UUID

class SupabaseRepository {
    private val client = if (BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()) {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Postgrest)
        }
    } else {
        null
    }

    suspend fun uploadSession(report: SessionReport, records: List<RepRecord>): Result<Unit> {
        val supabase = client ?: return Result.failure(IllegalStateException("Supabase 설정이 없습니다."))
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
                    feedbackMessage = report.feedbackMessage
                )
            )
            if (records.isNotEmpty()) {
                supabase.from("exercise_rep_records").insert(
                    records.map { record ->
                        RepRow(
                            sessionId = sessionId,
                            repNumber = record.repNumber,
                            maxAngle = record.maxAngle,
                            swayX = record.swayX
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
        val feedbackMessage: String
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
        val swayX: Float
    )
}