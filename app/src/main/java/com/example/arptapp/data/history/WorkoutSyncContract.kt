package com.example.arptapp.data.history

import com.example.arptapp.data.ExerciseRecord

/** An account epoch changes at logout/account replacement, even A -> B -> A. No tokens here. */
data class WorkoutAccount(val userId: String, val epoch: Long)

interface WorkoutAccounts {
    fun current(): WorkoutAccount?
    fun requireCurrent(account: WorkoutAccount) {
        check(account == current()) { "운동 기록 계정이 변경되었습니다." }
    }
}

enum class WorkoutSyncState { PENDING, SYNCED, FAILED, LOCAL_ONLY }
enum class SyncOutcome { SUCCESS, FAILED, PARTIAL, SKIPPED, ACCOUNT_CHANGED, DISABLED }

interface WorkoutLocalDataSource {
    suspend fun records(userId: String): List<ExerciseRecord>
    suspend fun save(record: ExerciseRecord): ExerciseRecord
    suspend fun find(userId: String, sessionId: String): ExerciseRecord?
    suspend fun pending(userId: String, limit: Int): List<ExerciseRecord>
    suspend fun merge(record: ExerciseRecord)
    suspend fun state(userId: String, sessionId: String, state: WorkoutSyncState)
    suspend fun details(userId: String, sessionId: String, detail: WorkoutDetails)
}

data class WorkoutDetails(val analysesJson: String, val repRecordsJson: String)

/** Bounded page of summaries only. Heavy report JSON and rep rows are fetched on selection. */
interface WorkoutRemoteDataSource {
    suspend fun upload(account: WorkoutAccount, record: ExerciseRecord)
    suspend fun summaries(account: WorkoutAccount, offset: Int, limit: Int): List<ExerciseRecord>
    suspend fun details(account: WorkoutAccount, sessionId: String): WorkoutDetails
}
