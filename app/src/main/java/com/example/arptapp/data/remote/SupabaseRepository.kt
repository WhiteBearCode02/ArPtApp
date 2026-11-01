package com.example.arptapp.data.remote

import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.data.history.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.*

/** supabase-kt 3.0.0 adapter. Owner filters supplement, never replace server RLS. */
class SupabaseRepository(private val accounts: WorkoutAccounts = WorkoutHistoryProvider.accounts) : WorkoutRemoteDataSource {
    private val client get() = SupabaseClientProvider.client
    private val json = Json { encodeDefaults = true }
    private fun authorize(account: WorkoutAccount) {
        accounts.requireCurrent(account)
        check(client.auth.currentUserOrNull()?.id == account.userId) { "인증된 기록 소유자가 아닙니다." }
    }
    override suspend fun upload(account: WorkoutAccount, record: ExerciseRecord) {
        authorize(account)
        require(record.userId == account.userId)
        val snapshot = WorkoutRemoteMapper.upload(record)
        val reps = WorkoutRemoteMapper.reps(record)
        // SECURITY INVOKER transaction: repeated UUIDs never create a second session.
        client.postgrest.rpc("save_workout_snapshot", buildJsonObject {
            put("p_session", json.encodeToJsonElement(RemoteWorkoutUpload.serializer(), snapshot))
            put("p_reps", JsonArray(reps.map { json.encodeToJsonElement(RemoteRepRecord.serializer(), it) }))
        })
        authorize(account)
    }
    override suspend fun summaries(account: WorkoutAccount, offset: Int, limit: Int): List<ExerciseRecord> {
        authorize(account)
        require(offset >= 0 && limit in 1..WorkoutHistoryRepository.PAGE_SIZE)
        val rows = client.from("exercise_sessions").select(Columns.list(
            "id", "user_id", "exercise_type", "total_reps", "average_score", "feedback_message",
            "duration_seconds", "burned_calories", "created_at", "workout_date", "exact_average_score"
        )) {
            filter { eq("user_id", account.userId) }
            order("created_at", Order.DESCENDING); order("id", Order.DESCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<RemoteExerciseSession>()
        authorize(account)
        return rows.map { WorkoutRemoteMapper.summary(it, account.userId) }
    }
    override suspend fun details(account: WorkoutAccount, sessionId: String): WorkoutDetails {
        authorize(account)
        require(sessionId.isNotBlank())
        // Ownership proof is required even for accounts with a trusted admin role.
        val header = client.from("exercise_sessions").select(Columns.list("id", "user_id", "report_json")) {
            filter { eq("id", sessionId); eq("user_id", account.userId) }; limit(1)
        }.decodeList<RemoteDetail>().single()
        authorize(account)
        check(header.id == sessionId && header.userId == account.userId)
        val reps = client.from("exercise_rep_records").select(Columns.list(
            "session_id", "rep_number", "max_angle", "sway_x", "score", "detail", "error_tags",
            "eccentric_duration_ms", "concentric_duration_ms", "pose_score"
        )) {
            filter { eq("session_id", sessionId) }; order("rep_number", Order.ASCENDING)
            limit((WorkoutPayloadCodec.MAX_REPS + 1).toLong())
        }.decodeList<RemoteRepRecord>()
        authorize(account)
        require(reps.size <= WorkoutPayloadCodec.MAX_REPS && reps.all { it.sessionId == sessionId })
        return WorkoutDetails(header.reportJson?.let(WorkoutPayloadCodec::analyses)
            ?: WorkoutRemoteMapper.legacyAnalyses(reps), WorkoutPayloadCodec.reps(reps.map { it.localRep() }))
    }
    @Serializable private data class RemoteDetail(val id: String, @SerialName("user_id") val userId: String,
        @SerialName("report_json") val reportJson: String? = null)
}
