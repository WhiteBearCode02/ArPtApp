package com.example.arptapp.data.history

import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.data.remote.*
import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.domain.report.StructuredWorkoutAnalysis
import com.example.arptapp.model.RepAnalysis
import com.example.arptapp.model.RepRecord
import com.example.arptapp.presentation.report.WorkoutReportData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Executable contract tests with in-memory ports; these do NOT claim a live Supabase/RLS pass. */
class WorkoutHistoryRepositoryTest {
    private val a = WorkoutAccount("account-a", 1)
    private class Accounts(var value: WorkoutAccount?) : WorkoutAccounts { override fun current() = value }
    private class Local : WorkoutLocalDataSource {
        val rows = mutableListOf<ExerciseRecord>()
        override suspend fun records(userId: String) = rows.filter { it.userId == userId }
        override suspend fun find(userId: String, sessionId: String) = rows.find { it.userId == userId && it.sessionId == sessionId }
        override suspend fun save(record: ExerciseRecord): ExerciseRecord {
            val existing = rows.find { it.sessionId == record.sessionId }
            require(existing == null || existing.userId == record.userId)
            return existing ?: record.copy(id = rows.size + 1).also { rows.add(it) }
        }
        override suspend fun pending(userId: String, limit: Int) = rows.filter {
            it.userId == userId && it.cloudEligible && it.sessionId != null && it.syncState in listOf("PENDING", "FAILED")
        }.take(limit)
        override suspend fun merge(record: ExerciseRecord) {
            val old = find(record.userId, requireNotNull(record.sessionId))
            if (old == null) save(record)
            else if (old.syncState == "SYNCED") {
                rows[rows.indexOf(old)] = record.copy(id = old.id, scoresJson = old.scoresJson,
                    yoloDiagnosticsJson = old.yoloDiagnosticsJson, detailsCached = old.detailsCached,
                    repRecordsJson = old.repRecordsJson)
            }
        }
        override suspend fun state(userId: String, sessionId: String, state: WorkoutSyncState) {
            val old = find(userId, sessionId) ?: return
            rows[rows.indexOf(old)] = old.copy(syncState = state.name)
        }
        override suspend fun details(userId: String, sessionId: String, detail: WorkoutDetails) {
            val old = find(userId, sessionId) ?: return
            rows[rows.indexOf(old)] = old.copy(scoresJson = detail.analysesJson,
                repRecordsJson = detail.repRecordsJson, detailsCached = true)
        }
    }
    private class Remote : WorkoutRemoteDataSource {
        var fail = false
        var afterFetch: () -> Unit = {}
        var afterUpload: () -> Unit = {}
        var pages = mutableListOf<Int>()
        var fetches = 0
        var detailCalls = 0
        val uploads = mutableSetOf<String>()
        var list = emptyList<ExerciseRecord>()
        override suspend fun upload(account: WorkoutAccount, record: ExerciseRecord) {
            if (fail) error("offline")
            uploads.add(requireNotNull(record.sessionId)); afterUpload()
        }
        override suspend fun summaries(account: WorkoutAccount, offset: Int, limit: Int): List<ExerciseRecord> {
            fetches++; pages.add(offset)
            if (fail) error("offline")
            afterFetch()
            return list.drop(offset).take(limit)
        }
        override suspend fun details(account: WorkoutAccount, sessionId: String): WorkoutDetails {
            detailCalls++
            if (fail) error("offline")
            afterFetch()
            return WorkoutDetails("[80,90]", "[]")
        }
    }
    private fun row(id: String = "8dc65964-c7bf-4d84-8ab2-b1f9301bedb1", owner: String = a.userId) = ExerciseRecord(
        userId = owner, date = "2026-11-01 20:00", totalCount = 2, duration = 60,
        burnedCalories = 1.0, exerciseType = "스쿼트", averageScore = 85.5f, scoresJson = "[80,91]",
        yoloDiagnosticsJson = "private-diagnostics", sessionId = id, syncState = "PENDING",
        cloudEligible = true, createdAt = "2026-11-01T11:00:00Z")

    @Test fun currentOwnerCacheNeverReturnsOtherAccounts() = runBlocking {
        val local = Local(); val accounts = Accounts(a)
        local.rows += listOf(row(), row("other", "account-b"))
        val repo = WorkoutHistoryRepository(local, Remote(), accounts)
        assertEquals(listOf(a.userId), repo.cached(a).map { it.userId })
        accounts.value = WorkoutAccount("account-b", 2)
        assertTrue(runCatching { repo.cached(a) }.isFailure)
        assertEquals("account-b", repo.cached(accounts.value!!).single().userId)
    }
    @Test fun sameSessionSavesAndUploadsOnceAcrossRetry() = runBlocking {
        val local = Local(); val remote = Remote(); val repo = WorkoutHistoryRepository(local, remote, Accounts(a))
        repo.save(a, row()); repo.save(a, row()); repo.syncCurrentUser(true)
        assertEquals(1, local.rows.size); assertEquals(1, remote.uploads.size)
        assertEquals("SYNCED", local.rows.single().syncState)
    }
    @Test fun offlineSaveRetainsFullLocalReportAndRetriesSameId() = runBlocking {
        val local = Local(); val remote = Remote().apply { fail = true }
        val repo = WorkoutHistoryRepository(local, remote, Accounts(a))
        repo.save(a, row())
        assertEquals("FAILED", local.rows.single().syncState)
        assertEquals("[80,91]", local.rows.single().scoresJson)
        assertEquals("private-diagnostics", local.rows.single().yoloDiagnosticsJson)
        remote.fail = false; assertEquals(SyncOutcome.SUCCESS, repo.syncCurrentUser(true))
        assertEquals(setOf(row().sessionId), remote.uploads)
    }
    @Test fun remoteSummaryMergesWithoutLosingDeviceOnlyDetail() = runBlocking {
        val local = Local(); val remote = Remote(); val repo = WorkoutHistoryRepository(local, remote, Accounts(a))
        repo.save(a, row())
        remote.list = listOf(row().copy(syncState = "SYNCED", detailsCached = false, scoresJson = "[]", yoloDiagnosticsJson = null))
        repo.syncCurrentUser(true)
        assertEquals(1, local.rows.size); assertTrue(local.rows.single().detailsCached)
        assertEquals("private-diagnostics", local.rows.single().yoloDiagnosticsJson)
        assertEquals("[80,91]", local.rows.single().scoresJson)
    }
    @Test fun remoteOnlySummaryFetchesDetailOnlyOnSelectionAndCachesIt() = runBlocking {
        val local = Local(); val remote = Remote().apply { list = listOf(row().copy(syncState = "SYNCED", detailsCached = false)) }
        val repo = WorkoutHistoryRepository(local, remote, Accounts(a))
        repo.syncCurrentUser(true); assertEquals(0, remote.detailCalls)
        val loaded = repo.detailed(a, local.rows.single()); assertEquals("[80,90]", loaded.scoresJson)
        remote.fail = true; assertEquals(loaded, repo.detailed(a, loaded)); assertEquals(1, remote.detailCalls)
    }
    @Test fun legacyAndExplicitLocalValidationRowsNeverUpload() = runBlocking {
        val local = Local(); val remote = Remote(); val repo = WorkoutHistoryRepository(local, remote, Accounts(a))
        local.rows += row().copy(sessionId = null, cloudEligible = false, syncState = "LOCAL_ONLY")
        repo.save(a, row("local-validation").copy(cloudEligible = false, syncState = "LOCAL_ONLY"))
        repo.syncCurrentUser(true); repo.syncCurrentUser(true)
        assertEquals(2, local.rows.size); assertTrue(remote.uploads.isEmpty())
    }
    @Test fun accountSwitchAfterFetchRejectsOldRowsEvenAfterReturningToA() = runBlocking {
        val local = Local(); val accounts = Accounts(a)
        val remote = Remote().apply { list = listOf(row()); afterFetch = { accounts.value = a.copy(epoch = 3) } }
        val repo = WorkoutHistoryRepository(local, remote, accounts)
        assertEquals(SyncOutcome.ACCOUNT_CHANGED, repo.syncCurrentUser(true)); assertTrue(local.rows.isEmpty())
    }
    @Test fun accountSwitchDuringUploadDoesNotAcknowledgeOrReattributeOldSave() = runBlocking {
        val local = Local(); val accounts = Accounts(a)
        val remote = Remote().apply { afterUpload = { accounts.value = WorkoutAccount("b", 2) } }
        val repo = WorkoutHistoryRepository(local, remote, accounts)
        runCatching { repo.save(a, row()) }
        assertEquals(a.userId, local.rows.single().userId); assertEquals("PENDING", local.rows.single().syncState)
    }
    @Test fun rejectsMismatchedRemoteOwner() = runBlocking {
        val local = Local(); val remote = Remote().apply { list = listOf(row(owner = "b")) }
        assertEquals(SyncOutcome.FAILED, WorkoutHistoryRepository(local, remote, Accounts(a)).syncCurrentUser(true))
        assertTrue(local.rows.isEmpty())
    }
    @Test fun boundedPagesContinueOnExplicitRetryAndNeverPoll() = runBlocking {
        val local = Local(); val remote = Remote().apply { list = (0..500).map { row("session-$it").copy(syncState = "SYNCED") } }
        val repo = WorkoutHistoryRepository(local, remote, Accounts(a), now = { 0L })
        assertEquals(SyncOutcome.PARTIAL, repo.syncCurrentUser(true)); assertEquals(500, local.rows.size)
        assertEquals(SyncOutcome.SKIPPED, repo.syncCurrentUser()); assertEquals(5, remote.fetches)
        assertEquals(SyncOutcome.SUCCESS, repo.syncCurrentUser(true)); assertEquals(501, local.rows.size)
        assertEquals(500, remote.pages.last())
    }
    @Test fun uploadBatchLeavesRemainderForExplicitRetry() = runBlocking {
        val local = Local(); local.rows += (1..25).map { row("session-$it") }
        val remote = Remote(); val repo = WorkoutHistoryRepository(local, remote, Accounts(a))
        assertEquals(SyncOutcome.PARTIAL, repo.syncCurrentUser(true)); assertEquals(20, remote.uploads.size)
        assertEquals(SyncOutcome.SUCCESS, repo.syncCurrentUser(true)); assertEquals(25, remote.uploads.size)
    }
    @Test fun cancellationPreservesPendingRow() = runBlocking {
        val local = Local(); val remote = Remote().apply { afterUpload = { throw CancellationException("test") } }
        assertTrue(runCatching { WorkoutHistoryRepository(local, remote, Accounts(a)).save(a, row()) }.exceptionOrNull() is CancellationException)
        assertEquals("PENDING", local.rows.single().syncState)
    }
    @Test fun localAcknowledgementHappensBeforeAnyRemoteWork() = runBlocking {
        val local = Local(); var acknowledged = false
        val remote = Remote().apply { afterUpload = { assertTrue(acknowledged) } }
        val repo = WorkoutHistoryRepository(local, remote, Accounts(a))
        repo.save(a, row()) { assertEquals(1, local.rows.size); acknowledged = true }
        assertTrue(acknowledged)
    }
    @Test fun accountSwitchDuringLazyDetailsCannotCacheOldResponse() = runBlocking {
        val local = Local(); val accounts = Accounts(a)
        local.rows += row().copy(syncState = "SYNCED", detailsCached = false, scoresJson = "[]")
        val remote = Remote().apply { afterFetch = { accounts.value = WorkoutAccount("b", 2) } }
        assertTrue(runCatching { WorkoutHistoryRepository(local, remote, accounts).detailed(a, local.rows.single()) }.isFailure)
        assertFalse(local.rows.single().detailsCached)
        assertEquals("[]", local.rows.single().scoresJson)
    }
    @Test fun summaryRoundTripKeepsExactScoreAndDate() {
        val upload = WorkoutRemoteMapper.upload(row())
        assertEquals("SQUAT", upload.exerciseType)
        val summary = RemoteExerciseSession(upload.id, upload.userId, upload.exerciseType, upload.totalReps,
            upload.averageScore, upload.feedbackMessage, upload.durationSeconds, upload.burnedCalories,
            upload.createdAt, upload.workoutDate, upload.exactAverageScore)
        val restored = WorkoutRemoteMapper.summary(summary, a.userId)
        assertEquals("스쿼트", restored.exerciseType)
        assertEquals(row().averageScore, restored.averageScore, 0f); assertEquals(row().date, restored.date)
        assertEquals(row().sessionId, restored.sessionId); assertFalse(restored.detailsCached)
        assertEquals("[80,91]", row().scoresJson)
    }
    @Test fun reportAndRepRoundTripRestoresActualRecordedValues() {
        val analyses = listOf(RepAnalysis(1, 85f, "회차 관찰 내용"))
        val raw = StructuredReportCodec.encode(analyses, StructuredWorkoutAnalysis(exerciseType = "SQUAT", totalReps = 1, repAnalyses = emptyList()))
        val rep = RepRecord(1, 110.5, .1f, listOf("TEST"), 100, 200, 90f)
        val source = row().copy(scoresJson = raw, repRecordsJson = WorkoutPayloadCodec.reps(listOf(rep)))
        val dto = WorkoutRemoteMapper.upload(source)
        assertEquals(raw, dto.reportJson)
        assertEquals(rep, WorkoutRemoteMapper.reps(source).single().localRep())
        assertEquals(85f, WorkoutRemoteMapper.reps(source).single().score!!, 0f)
        val restored = WorkoutReportData.fromRecord(source)
        assertEquals(raw, restored.analysesJson); assertEquals(source.repRecordsJson, restored.repRecordsJson)
    }
    @Test fun wireDtoExcludesDeviceDiagnosticsAndCredentials() {
        val encoded = Json.encodeToString(RemoteWorkoutUpload.serializer(), WorkoutRemoteMapper.upload(row()))
        assertFalse(encoded.contains("private-diagnostics")); assertFalse(encoded.contains("yolo", true))
        assertFalse(encoded.contains("password", true)); assertFalse(encoded.contains("token", true))
        val raw = "{\"analyses\":[],\"yoloDiagnosticsJson\":\"private\"}"
        assertTrue(runCatching { WorkoutPayloadCodec.analyses(raw) }.isFailure)
    }
    @Test fun legacyServerRepFallbackDoesNotInventMissingScore() {
        val reps = listOf(RemoteRepRecord("session", 1, 100.0, 0f, null), RemoteRepRecord("session", 2, 90.0, 0f, 80f, "기록"))
        val raw = WorkoutRemoteMapper.legacyAnalyses(reps)
        assertEquals(1, com.example.arptapp.data.RepAnalysisCodec.decode(raw).size)
        assertEquals(2, com.example.arptapp.data.RepAnalysisCodec.decode(raw).single().repNumber)
    }
}
