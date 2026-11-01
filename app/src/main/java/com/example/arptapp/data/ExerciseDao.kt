package com.example.arptapp.data

// [Imports: Room 데이터 접근을 위한 어노테이션들을 불러옵니다]
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import androidx.room.Transaction
import androidx.room.Update

/**
 * [AIRPTCoach - 운동 기록 데이터 접근 객체 (DAO)]
 * * 역할:
 * - 데이터베이스(DB)에 접근하여 데이터를 저장, 조회, 삭제하는 모든 명령을 관리하는 통로입니다.
 * - SQL 쿼리문을 직접 작성하거나 Room에서 제공하는 편리한 기능을 사용하여 데이터를 제어합니다.
 * - @Dao: 이 인터페이스가 데이터베이스에 접근하는 객체임을 Room 엔진에게 알려줍니다.
 */
@Dao
interface ExerciseDao {

    /**
     * [운동 기록 저장하기]
     * - 역할: 사용자가 운동을 마쳤을 때 생성된 ExerciseRecord 객체를 DB 테이블에 한 줄 추가합니다.
     * - @Insert: 별도의 SQL문 없이도 데이터를 삽입해주는 편리한 기능입니다.
     */
    @Insert
    suspend fun insertRecord(record: ExerciseRecord)

    /**
     * [전체 운동 기록 불러오기]
     * - 역할: 저장된 모든 운동 데이터를 시간순(id 역순)으로 정렬하여 리스트 형태로 가져옵니다.
     * - @Query: 직접 SQL문을 작성하여 데이터를 조회합니다.
     * - "SELECT * FROM exercise_records ORDER BY id DESC"
     * -> 'exercise_records' 테이블의 모든 열을 가져오되, id 번호가 큰 것(최신순)부터 정렬하라는 뜻입니다.
     */
    @Query("SELECT * FROM exercise_records WHERE userId = :userId ORDER BY date DESC, id DESC")
    suspend fun getAllRecords(userId: String): List<ExerciseRecord>

    @Query("SELECT * FROM exercise_records WHERE userId = :userId AND sessionId = :sessionId LIMIT 1")
    suspend fun findSession(userId: String, sessionId: String): ExerciseRecord?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(record: ExerciseRecord): Long

    @Update
    suspend fun updateSession(record: ExerciseRecord)

    /** INSERT IGNORE + owner-scoped read is idempotent and never REPLACE-deletes local diagnostics. */
    @Transaction
    suspend fun saveSession(record: ExerciseRecord): ExerciseRecord {
        require(!record.sessionId.isNullOrBlank() && record.userId.isNotBlank())
        insertSession(record)
        return checkNotNull(findSession(record.userId, record.sessionId))
    }

    @Transaction
    suspend fun mergeSummary(summary: ExerciseRecord) {
        val existing = findSession(summary.userId, requireNotNull(summary.sessionId))
        if (existing == null) {
            saveSession(summary)
        } else if (existing.syncState == "SYNCED") {
            // A summary never downgrades cached detailed analyses or the device-only envelope.
            updateSession(summary.copy(id = existing.id, scoresJson = existing.scoresJson,
                detailsCached = existing.detailsCached, repRecordsJson = existing.repRecordsJson,
                yoloDiagnosticsJson = existing.yoloDiagnosticsJson))
        }
    }

    @Query("UPDATE exercise_records SET syncState = :state WHERE userId = :userId AND sessionId = :sessionId AND cloudEligible = 1")
    suspend fun setSyncState(userId: String, sessionId: String, state: String)

    @Query("UPDATE exercise_records SET scoresJson = :analyses, repRecordsJson = :reps, detailsCached = 1 WHERE userId = :userId AND sessionId = :sessionId AND syncState = 'SYNCED'")
    suspend fun cacheDetails(userId: String, sessionId: String, analyses: String, reps: String)

    @Query("SELECT * FROM exercise_records WHERE userId = :userId AND cloudEligible = 1 AND sessionId IS NOT NULL AND syncState IN ('PENDING', 'FAILED') ORDER BY id LIMIT :limit")
    suspend fun pendingSessions(userId: String, limit: Int): List<ExerciseRecord>

    /**
     * [특정 기간 기록 삭제하기 (선택 사항)]
     * - 역할: 특정 id를 가진 데이터를 삭제하거나 테이블 전체를 비울 때 사용합니다.
     */
    @Query("DELETE FROM exercise_records")
    suspend fun deleteAllRecords()
}
