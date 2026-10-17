package com.example.arptapp.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.detection.DetectionSource
import com.example.arptapp.domain.detection.DetectorStatus
import com.example.arptapp.domain.detection.ExerciseDetectionResult
import com.example.arptapp.domain.detection.ExportContractVerification
import com.example.arptapp.domain.detection.YoloSessionDiagnosticsBridge
import com.example.arptapp.domain.detection.YoloShadowSnapshot
import com.example.arptapp.presentation.report.ReportNavigation
import com.example.arptapp.presentation.report.WorkoutReportData
import com.example.arptapp.presentation.report.YoloSessionDiagnosticsPresentation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Synthetic fixtures live ONLY in Room.inMemoryDatabaseBuilder and are closed after each test.
 * This is integration coverage, not a physical exercise/device inference/persisted-user-record claim.
 * No fixture is inserted into AppDatabase.getDatabase(), no named database or account is touched.
 */
@RunWith(AndroidJUnit4::class)
class YoloSessionLocalRoundTripTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun frozenUnverifiedSessionRoundTripsRoomAndHistoryReportWithoutScoreMutation() = runBlocking {
        val bridge = YoloSessionDiagnosticsBridge(monotonicMs = { 200 })
        bridge.onResult(null, YoloShadowSnapshot(ExerciseDetectionResult(
            status = DetectorStatus.CONTRACT_NOT_VERIFIED, source = DetectionSource.YOLO,
            contractVerification = ExportContractVerification.NOT_VERIFIED)), isFrame = false)
        bridge.begin(1_000, 100, CameraView.SIDE)
        bridge.updateContext(ExerciseType.SQUAT, 2)
        val contextBeforeFinish = requireNotNull(bridge.captureContext())
        bridge.onFrameSeen(contextBeforeFinish)
        bridge.onFrameSeen(contextBeforeFinish)
        val frozen = requireNotNull(bridge.finish(2_000, 1_100, ExerciseType.SQUAT, 2))
        val diagnostics = requireNotNull(YoloSessionDiagnosticsCodec.encode(frozen))
        // A late callback cannot rewrite the already-frozen envelope attached to this report.
        bridge.onFrameSeen(contextBeforeFinish)
        assertNull(bridge.current(3_000, 2_100))

        val source = WorkoutReportData.create(exerciseType = "SQUAT", totalCount = 2,
            averageScore = 85f, analysesJson = "[80,90]", durationSeconds = 12,
            burnedCalories = 1.0, feedbackMessage = "fixture-only", yoloDiagnosticsJson = diagnostics)
            .completedAt("2000-01-01 00:00")
        withInMemoryDatabase { database ->
            database.exerciseDao().insertRecord(source.toRecord("in-memory-fixture-owner"))
            val loaded = database.exerciseDao().getAllRecords("in-memory-fixture-owner").single()
            val report = WorkoutReportData.fromRecord(loaded)
            val navigation = ReportNavigation.read(ReportNavigation.intent(context, report))
            assertEquals(source, report)
            assertEquals(report, navigation)
            assertEquals(2, loaded.totalCount)
            assertEquals(85f, loaded.averageScore, 0f)
            assertEquals("[80,90]", loaded.scoresJson)
            assertEquals(diagnostics, loaded.yoloDiagnosticsJson)
            val persisted = requireNotNull(YoloSessionDiagnosticsCodec.decode(navigation.yoloDiagnosticsJson))
            assertEquals("CONTRACT_NOT_VERIFIED", persisted.modelStatus)
            assertEquals("NOT_VERIFIED", persisted.contractStatus)
            assertFalse(persisted.predictionEnabled)
            assertEquals(2L, persisted.totalFramesSeen)
            assertEquals(0L, persisted.processedFrames)
            assertNull(persisted.averageConfidence)
            assertNull(persisted.averageTotalMs)
            assertEquals("SQUAT", persisted.expectedSessionExercise)
            assertEquals("SIDE", persisted.cameraView)
            assertEquals(1_000L, persisted.startedAt)
            assertEquals(2_000L, persisted.endedAt)
            assertNotNull(YoloSessionDiagnosticsPresentation.visibleDiagnostics(true, navigation.yoloDiagnosticsJson))
            assertNull(YoloSessionDiagnosticsPresentation.visibleDiagnostics(false, navigation.yoloDiagnosticsJson))
        }
    }

    @Test fun legacyNullDiagnosticsRemainsReadableAndCannotCreateAnApparentYoloResult() = runBlocking {
        val source = WorkoutReportData.create(exerciseType = "SQUAT", totalCount = 3,
            averageScore = 70f, analysesJson = "[70]", durationSeconds = 15,
            yoloDiagnosticsJson = null).completedAt("2000-01-01 00:00")
        withInMemoryDatabase { database ->
            database.exerciseDao().insertRecord(source.toRecord("in-memory-legacy-owner"))
            val loaded = database.exerciseDao().getAllRecords("in-memory-legacy-owner").single()
            assertNull(loaded.yoloDiagnosticsJson)
            val report = WorkoutReportData.fromRecord(loaded)
            assertEquals(source, report)
            assertNull(ReportNavigation.read(ReportNavigation.intent(context, report)).yoloDiagnosticsJson)
            assertNull(YoloSessionDiagnosticsPresentation.visibleDiagnostics(true, report.yoloDiagnosticsJson))
            assertEquals("[70]", loaded.scoresJson)
            assertEquals(3, loaded.totalCount)
        }
    }

    private suspend fun withInMemoryDatabase(block: suspend (AppDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
