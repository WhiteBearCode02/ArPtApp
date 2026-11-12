package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import org.junit.Assert.*
import org.junit.Test

class ExpandedYoloApplicabilityTest {
    @Test fun processedTwoClassFramesRemainExcludedForAllFiveUnsupportedSessions() {
        for (exercise in ExerciseType.supported.drop(2)) {
            val collector = YoloSessionDiagnosticsCollector()
            val snapshot = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
                ExerciseDetection(ExerciseType.SQUAT, .8f, DetectionSource.YOLO),
                timings = DetectionTimings(2, 8, 3, 13),
                contractVerification = ExportContractVerification.VERIFIED, shadowRuntimeEnabled = true), ExerciseType.SQUAT)
            val token = collector.reset(1000, 0, CameraView.FRONT, snapshot)
            repeat(2) {
                collector.observeFrameSeen(token); collector.observeAdmission(token)
                collector.observeSnapshot(token, snapshot, exercise, 0, 500L + it * 100)
            }
            val loaded = requireNotNull(YoloSessionDiagnosticsCodec.decode(requireNotNull(
                YoloSessionDiagnosticsCodec.encode(requireNotNull(collector.finish(token, 2000, 1000, exercise, 0))))))
            assertEquals(exercise.name, loaded.expectedSessionExercise)
            assertEquals(2L, loaded.processedFrames); assertEquals(2L, loaded.unknownSessionFrames)
            assertEquals(2L, loaded.rawDetectionCounts["SQUAT"])
            assertEquals(setOf("SQUAT", "SHOULDER_PRESS", "UNKNOWN"), loaded.rawDetectionCounts.keys)
            assertEquals(0L, loaded.matchingStableFrames + loaded.mismatchingStableFrames + loaded.unknownStableFrames)
            assertNull(loaded.knownMatchRate); assertNull(loaded.unknownStableRate)
            assertNull(YoloSessionDiagnosticsCodec.encode(loaded.copy(mismatchingStableFrames = 1, unknownSessionFrames = 1)))
        }
    }
    @Test fun newExercisesCannotBecomeYoloClassesOrMismatchRates() {
        assertEquals(mapOf(0 to ExerciseType.SQUAT,1 to ExerciseType.SHOULDER_PRESS),YoloModelPolicy.classMapping)
        for(exercise in ExerciseType.supported.drop(2)) {
            val d=YoloSessionDiagnostics(modelStatus="READY",contractStatus="VERIFIED",predictionEnabled=true,
                expectedSessionExercise=exercise.name,cameraView=CameraView.FRONT.name,startedAt=0,endedAt=1000,shadowRuntimeEnabled=true)
            val loaded=YoloSessionDiagnosticsCodec.decode(requireNotNull(YoloSessionDiagnosticsCodec.encode(d)))!!
            assertEquals("UNSUPPORTED_EXERCISE",loaded.comparisonApplicability)
            assertNull(loaded.knownMatchRate);assertNull(loaded.unknownStableRate)
            assertNull(YoloSessionDiagnosticsCodec.encode(d.copy(mismatchingStableFrames=1)))
        }
    }
}
