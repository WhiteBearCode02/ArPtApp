package com.example.arptapp.domain.detection

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.*
import org.junit.Test

class YoloSessionDiagnosticsBridgeTest {
    private val now = AtomicLong(1500)
    private val bridge = YoloSessionDiagnosticsBridge(monotonicMs = now::get)
    private fun snapshot(status: DetectorStatus = DetectorStatus.READY, timed: Boolean = false,
                         stable: ExerciseType = ExerciseType.SQUAT) = YoloShadowSnapshot(
        ExerciseDetectionResult(status, DetectionSource.YOLO,
            if (timed) ExerciseDetection(ExerciseType.SQUAT, .8f, DetectionSource.YOLO) else null,
            timings = if (timed) DetectionTimings(1, 2, 3, 6) else null,
            contractVerification = if (status == DetectorStatus.READY) ExportContractVerification.VERIFIED else ExportContractVerification.NOT_VERIFIED), stable)
    private fun begin() = bridge.begin(10_000, 1000, CameraView.SIDE)
    private fun finish() = requireNotNull(bridge.finish(11_000, 2000, ExerciseType.SQUAT, 2))
    private fun admit(): YoloShadowObservationContext = requireNotNull(bridge.captureContext()).also {
        bridge.onFrameSeen(it); bridge.onAdmission(it)
    }

    @Test fun modelMissingAvailabilityBeforeBeginIsSavedAsZeroFrameSession() {
        bridge.onResult(null, snapshot(DetectorStatus.MODEL_MISSING), false)
        begin()
        val live = requireNotNull(bridge.current(10_500, 1500))
        assertEquals("MODEL_MISSING", live.modelStatus); assertEquals(0L, live.processedFrames)
        val result = finish()
        assertEquals("MODEL_MISSING", result.modelStatus); assertEquals("NOT_VERIFIED", result.contractStatus)
        assertFalse(result.predictionEnabled); assertNull(result.averageProcessedFps)
        assertNull(bridge.captureContext()); assertNull(bridge.current(12_000, 3000))
    }

    @Test fun oldAdmissionContextCannotAddCountsOrStatusToNewSession() {
        begin(); val old = admit()
        bridge.begin(20_000, 2000, CameraView.FRONT)
        bridge.onResult(old, snapshot(timed = true), true)
        bridge.onFrameSeen(old); bridge.onAdmission(old)
        val current = requireNotNull(bridge.current(20_500, 2500))
        assertEquals(0L, current.totalFramesSeen); assertEquals(0L, current.admittedFrames)
        assertEquals(0L, current.processedFrames); assertEquals("FRONT", current.cameraView)
        assertEquals("INITIALIZING", current.modelStatus)
        bridge.begin(30_000, 3000, CameraView.SIDE)
        assertEquals("INITIALIZING", requireNotNull(bridge.current(30_500, 3500)).modelStatus)
    }

    @Test fun oldSessionNativeTimeoutPropagatesGlobalFailureWithoutAddingAnyFrame() {
        begin(); val old = admit()
        bridge.begin(20_000, 2000, CameraView.FRONT)
        bridge.updateContext(ExerciseType.SQUAT, 2)
        bridge.onResult(old, snapshot(DetectorStatus.TIMED_OUT), true)
        val current = requireNotNull(bridge.current(20_500, 2500))
        assertEquals("TIMED_OUT", current.modelStatus)
        assertFalse(current.predictionEnabled)
        assertEquals(0L, current.totalFramesSeen); assertEquals(0L, current.admittedFrames)
        assertEquals(0L, current.processedFrames); assertEquals(0L, current.matchingStableFrames)
        assertNull(current.averageConfidence)
    }

    @Test fun admissionWhilePrimaryUnknownStaysUncomparedAfterPrimaryLocks() {
        begin(); val unknownAdmission = admit()
        assertEquals(ExerciseType.UNKNOWN, unknownAdmission.expectedExercise)
        bridge.updateContext(ExerciseType.SQUAT, 1)
        bridge.onResult(unknownAdmission, snapshot(timed = true), true)
        val knownAdmission = admit()
        bridge.onResult(knownAdmission, snapshot(timed = true), true)
        val result = finish()
        assertEquals(2L, result.processedFrames); assertEquals(1L, result.unknownSessionFrames)
        assertEquals(1L, result.matchingStableFrames); assertEquals(0L, result.mismatchingStableFrames)
        assertEquals(1.0, result.knownMatchRate!!, .00001)
    }

    @Test fun seenAdmissionAndSuccessfulResultsStaySeparateAndInitializationIsNotProcessed() {
        begin(); bridge.updateContext(ExerciseType.SQUAT, 1)
        bridge.onResult(null, snapshot(), false)
        val seen = requireNotNull(bridge.captureContext())
        repeat(6) { bridge.onFrameSeen(seen) }
        repeat(2) { bridge.onAdmission(seen) }
        bridge.onResult(seen, snapshot(timed = true), true)
        bridge.onResult(seen, snapshot(DetectorStatus.INFERENCE_FAILED), true)
        val result = finish()
        assertEquals(6L, result.totalFramesSeen); assertEquals(2L, result.admittedFrames)
        assertEquals(1L, result.processedFrames); assertEquals(1.0, result.averageProcessedFps!!, .00001)
        assertEquals(6.0, result.averageTotalMs!!, .00001); assertEquals("INFERENCE_FAILED", result.modelStatus)
    }

    @Test fun backgroundAvailabilityUpdatesActiveSessionWithoutBecomingInferenceSample() {
        begin()
        val worker = Executors.newSingleThreadExecutor()
        try {
            worker.submit { bridge.onResult(null, snapshot(DetectorStatus.UNSUPPORTED_RUNTIME), false) }.get(5, TimeUnit.SECONDS)
            val result = finish()
            assertEquals("UNSUPPORTED_RUNTIME", result.modelStatus); assertEquals(0L, result.processedFrames)
            assertNull(result.averageInferenceMs); assertFalse(result.predictionEnabled)
        } finally { worker.shutdownNow() }
    }

    @Test fun liveSnapshotKeepsCollectingAndFinishIsSingleShot() {
        begin(); bridge.updateContext(ExerciseType.SQUAT, 1)
        bridge.onResult(admit(), snapshot(timed = true), true)
        val first = requireNotNull(bridge.current(10_500, 1500))
        bridge.onResult(admit(), snapshot(timed = true), true)
        val result = finish()
        assertEquals(1L, first.processedFrames); assertEquals(2L, result.processedFrames)
        assertNull(bridge.finish(12_000, 3000, ExerciseType.SQUAT, 3))
    }

    @Test fun resultWithoutAdmissionContextCannotBeAttributedToCurrentSession() {
        begin(); bridge.updateContext(ExerciseType.SQUAT, 0)
        bridge.onResult(null, snapshot(timed = true), true)
        assertEquals(0L, finish().processedFrames)
    }

    @Test fun contextCarriesImmutableThermalReadingAndMaximumIncludesFinalReading() {
        begin(); bridge.updateContext(ExerciseType.SQUAT, 3)
        val captured = admit()
        bridge.updateContext(ExerciseType.SHOULDER_PRESS, 1)
        assertEquals(ExerciseType.SQUAT, captured.expectedExercise); assertEquals(3, captured.thermalStatus)
        bridge.onResult(captured, snapshot(timed = true), true)
        val result = finish()
        assertEquals(3, result.maxThermalStatus); assertEquals(1L, result.matchingStableFrames)
    }
}
