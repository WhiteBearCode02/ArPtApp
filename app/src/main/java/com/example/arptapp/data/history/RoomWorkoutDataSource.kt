package com.example.arptapp.data.history

import com.example.arptapp.data.ExerciseDao
import com.example.arptapp.data.ExerciseRecord

class RoomWorkoutDataSource(private val dao: ExerciseDao) : WorkoutLocalDataSource {
    override suspend fun records(userId: String) = dao.getAllRecords(userId)
    override suspend fun save(record: ExerciseRecord) = dao.saveSession(record)
    override suspend fun find(userId: String, sessionId: String) = dao.findSession(userId, sessionId)
    override suspend fun pending(userId: String, limit: Int) = dao.pendingSessions(userId, limit)
    override suspend fun merge(record: ExerciseRecord) = dao.mergeSummary(record)
    override suspend fun state(userId: String, sessionId: String, state: WorkoutSyncState) =
        dao.setSyncState(userId, sessionId, state.name)
    override suspend fun details(userId: String, sessionId: String, detail: WorkoutDetails) =
        dao.cacheDetails(userId, sessionId, detail.analysesJson, detail.repRecordsJson)
}
