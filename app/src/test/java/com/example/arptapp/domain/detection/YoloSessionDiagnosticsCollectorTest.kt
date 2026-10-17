package com.example.arptapp.domain.detection

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class YoloSessionDiagnosticsCollectorTest {
    private val collector = YoloSessionDiagnosticsCollector()
    private fun initial(status: DetectorStatus = DetectorStatus.READY,
                        contract: ExportContractVerification = ExportContractVerification.VERIFIED) =
        YoloShadowSnapshot(ExerciseDetectionResult(status, DetectionSource.YOLO, contractVerification = contract, shadowRuntimeEnabled = true))
    private fun begin(snapshot: YoloShadowSnapshot = initial()) = collector.reset(10_000, 1000, CameraView.SIDE, snapshot)
    private fun frame(raw: ExerciseType = ExerciseType.SQUAT, stable: ExerciseType = raw, confidence: Float? = .8f,
                      timings: DetectionTimings? = DetectionTimings(2, 8, 3, 13)) =
        YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
            raw.takeIf { it != ExerciseType.UNKNOWN }?.let { ExerciseDetection(it, confidence, DetectionSource.YOLO) },
            timings = timings, contractVerification = ExportContractVerification.VERIFIED, shadowRuntimeEnabled = true), stable)
    private fun sample(token: Long, snapshot: YoloShadowSnapshot = frame(), expected: ExerciseType = ExerciseType.SQUAT,
                       thermal: Int = 0, time: Long = 1500) {
        collector.observeFrameSeen(token); collector.observeAdmission(token)
        collector.observeSnapshot(token, snapshot, expected, thermal, time)
    }
    private fun end(token: Long, expected: ExerciseType = ExerciseType.SQUAT, thermal: Int = -1,
                    time: Long = 2000) = requireNotNull(collector.finish(token, 11_000, time, expected, thermal))

    @Test fun modelMissingStillProducesUsefulZeroFrameSession() {
        val token = begin(initial(DetectorStatus.MODEL_MISSING, ExportContractVerification.NOT_VERIFIED))
        repeat(5) { collector.observeFrameSeen(token) }
        val result = end(token)
        assertEquals("MODEL_MISSING", result.modelStatus); assertEquals("NOT_VERIFIED", result.contractStatus)
        assertFalse(result.predictionEnabled); assertEquals(5L, result.totalFramesSeen); assertEquals(0L, result.processedFrames)
        assertNull(result.averageProcessedFps); assertNull(result.averageInferenceMs); assertNull(result.averageConfidence)
        assertEquals(YoloModelPolicy.MODEL_ID, result.modelId); assertEquals(YoloModelPolicy.CANONICAL_SHA256, result.modelSha256)
    }

    @Test fun resetClearsCountersAndRejectsOldCallbackTokens() {
        val old = begin(); sample(old)
        val current = begin(); assertNotEquals(old, current)
        sample(old); assertNull(collector.finish(old, 11_000, 2000, ExerciseType.SQUAT, 0))
        val result = end(current)
        assertEquals(0L, result.totalFramesSeen); assertEquals(0L, result.processedFrames)
    }

    @Test fun finishFreezesAndRejectsSubsequentCallbacks() {
        val token = begin(); sample(token)
        val result = end(token)
        sample(token, frame(ExerciseType.SHOULDER_PRESS))
        assertEquals(1L, result.processedFrames)
        assertEquals(1L, result.rawDetectionCounts["SQUAT"])
        assertNull(collector.finish(token, 12_000, 3000, ExerciseType.SQUAT, 5))
        assertNull(collector.snapshot(token, 12_000, 3000, ExerciseType.SQUAT, 5))
    }

    @Test fun frozenCountMapsCannotBeMutatedByConsumer() {
        val token = begin(); sample(token)
        val result = end(token)
        try {
            @Suppress("UNCHECKED_CAST")
            (result.stableDetectionCounts as MutableMap<String, Long>)["SQUAT"] = 99L
            fail("Frozen map must reject consumer mutation")
        } catch (_: UnsupportedOperationException) { }
    }

    @Test fun liveSnapshotDoesNotFinishAndEarlierSnapshotStaysIndependent() {
        val token = begin(); sample(token)
        val first = requireNotNull(collector.snapshot(token, 10_500, 1500, ExerciseType.SQUAT, 1))
        sample(token, frame(ExerciseType.SHOULDER_PRESS), time = 1750)
        val result = end(token)
        assertEquals(1L, first.processedFrames); assertEquals(2L, result.processedFrames)
        assertEquals(0L, first.rawDetectionCounts["SHOULDER_PRESS"])
        assertEquals(2.0, first.averageProcessedFps!!, 0.0001)
    }

    @Test fun seenAdmissionAndSuccessfulCompletionAreSeparateCounters() {
        val token = begin()
        repeat(10) { collector.observeFrameSeen(token) }
        repeat(3) { collector.observeAdmission(token) }
        collector.observeSnapshot(token, frame(), ExerciseType.SQUAT, 0, 1200)
        collector.observeSnapshot(token, initial(DetectorStatus.INFERENCE_FAILED), ExerciseType.SQUAT, 0, 1400)
        val result = end(token)
        assertEquals(10L, result.totalFramesSeen); assertEquals(3L, result.admittedFrames); assertEquals(1L, result.processedFrames)
        assertEquals("INFERENCE_FAILED", result.modelStatus); assertFalse(result.predictionEnabled)
        assertEquals(8.0, result.averageInferenceMs!!, .0001)
    }

    @Test fun initializationAndInvalidationDoNotCountEvenWithDefensiveTimings() {
        val token = begin(); collector.observeFrameSeen(token); collector.observeAdmission(token)
        collector.observeSnapshot(token, frame(), ExerciseType.SQUAT, 1, 1500, isFrame = false)
        collector.observeSnapshot(token, initial(), ExerciseType.SQUAT, 2, 1500)
        assertEquals(0L, end(token).processedFrames)
    }

    @Test fun noAdmissionMeansNoProcessedFrame() {
        val token = begin()
        collector.observeAdmission(token)
        collector.observeSnapshot(token, frame(), ExerciseType.SQUAT, 0, 1500)
        val result = end(token)
        assertEquals(0L, result.admittedFrames); assertEquals(0L, result.processedFrames)
    }

    @Test fun malformedOrClockReversedTimingDoesNotEnterMetrics() {
        val token = begin()
        sample(token, frame(timings = DetectionTimings(-1, 5, 2, 6)))
        sample(token, frame(), time = 999)
        val result = end(token)
        assertEquals(0L, result.processedFrames); assertNull(result.averageTotalMs)
    }

    @Test fun timingMeansUseOnlyNativeSuccessfulFramesAndFpsUsesActiveMonotonicDuration() {
        val token = begin()
        sample(token, frame(timings = DetectionTimings(2, 10, 4, 16)))
        sample(token, frame(timings = DetectionTimings(4, 20, 8, 32)))
        val result = end(token, time = 3000)
        assertEquals(1.0, result.averageProcessedFps!!, .0001)
        assertEquals(3.0, result.averagePreprocessMs!!, .0001); assertEquals(15.0, result.averageInferenceMs!!, .0001)
        assertEquals(6.0, result.averagePostprocessMs!!, .0001); assertEquals(24.0, result.averageTotalMs!!, .0001)
        assertEquals(2000L, result.activeDurationMs)
    }

    @Test fun noDetectionAndMissingConfidenceDoNotBecomeZeroConfidenceSamples() {
        val token = begin()
        sample(token, frame(confidence = .6f)); sample(token, frame(confidence = 1f))
        sample(token, frame(ExerciseType.UNKNOWN)); sample(token, frame(confidence = null))
        sample(token, frame(confidence = Float.NaN)); sample(token, frame(confidence = -1f))
        val result = end(token)
        assertEquals(6L, result.processedFrames); assertEquals(2L, result.confidenceSampleCount)
        assertEquals(.8, result.averageConfidence!!, .0001); assertEquals(.6, result.minConfidence!!, .0001)
        assertEquals(1.0, result.maxConfidence!!, .0001)
    }

    @Test fun unsupportedExerciseDoesNotCreateConfidenceOrArbitraryCountKey() {
        val token = begin(); sample(token, frame(ExerciseType.LUNGE))
        val result = end(token)
        assertEquals(1L, result.rawDetectionCounts["UNKNOWN"]); assertEquals(1L, result.stableDetectionCounts["UNKNOWN"])
        assertFalse(result.rawDetectionCounts.containsKey("LUNGE")); assertNull(result.averageConfidence)
    }

    @Test fun supportedLockComparesKnownAndUnknownSeparately() {
        val token = begin()
        repeat(3) { sample(token, frame()) }
        sample(token, frame(ExerciseType.SHOULDER_PRESS)); sample(token, frame(ExerciseType.UNKNOWN))
        val result = end(token)
        assertEquals(3L, result.matchingStableFrames); assertEquals(1L, result.mismatchingStableFrames)
        assertEquals(1L, result.unknownStableFrames)
        assertEquals(.75, result.knownMatchRate!!, .0001); assertEquals(.2, result.unknownStableRate!!, .0001)
    }

    @Test fun unknownPrimarySamplesAreNeverRetroactivelyCompared() {
        val token = begin()
        sample(token, frame(), ExerciseType.UNKNOWN)
        sample(token, frame(ExerciseType.SHOULDER_PRESS), ExerciseType.UNKNOWN)
        sample(token, frame(), ExerciseType.SQUAT)
        val result = end(token)
        assertEquals(2L, result.unknownSessionFrames); assertEquals(1L, result.matchingStableFrames)
        assertEquals(0L, result.mismatchingStableFrames); assertEquals(0L, result.unknownStableFrames)
        assertEquals(1.0, result.knownMatchRate!!, .0001)
    }

    @Test fun allUnknownPrimaryHasNoComparisonRate() {
        val token = begin(); sample(token, expected = ExerciseType.UNKNOWN)
        val result = end(token, ExerciseType.UNKNOWN)
        assertEquals("UNKNOWN", result.expectedSessionExercise); assertNull(result.knownMatchRate); assertNull(result.unknownStableRate)
    }

    @Test fun onlyUnknownStableHasNoKnownMatchRateButFullUnknownRate() {
        val token = begin(); sample(token, frame(ExerciseType.UNKNOWN))
        val result = end(token)
        assertNull(result.knownMatchRate); assertEquals(1.0, result.unknownStableRate!!, .0001)
    }

    @Test fun thermalMaximumIgnoresUnavailableAndInvalidValues() {
        val token = begin(); sample(token, thermal = 2); sample(token, thermal = 5); sample(token, thermal = -1)
        assertEquals(5, end(token, thermal = 99).maxThermalStatus)
        val second = begin(); assertNull(end(second).maxThermalStatus)
    }

    @Test fun contractNotVerifiedNeverAdvertisesPredictionEnabled() {
        val token = begin(initial(DetectorStatus.READY, ExportContractVerification.NOT_VERIFIED))
        val result = end(token)
        assertEquals("READY", result.modelStatus); assertFalse(result.predictionEnabled)
    }

    @Test fun disabledGateRejectsEvenAReadyTimedSnapshotAsInferenceEvidence() {
        val off = frame().let { it.copy(result = it.result.copy(shadowRuntimeEnabled = false)) }
        val token = begin(off)
        sample(token, off)
        val result = end(token)
        assertFalse(result.predictionEnabled); assertEquals(false, result.shadowRuntimeEnabled)
        assertEquals(0L, result.processedFrames); assertNull(result.averageConfidence)
    }

    @Test fun unknownViewIsPreservedAndWallClockReversalDoesNotInventNegativeDuration() {
        val token = collector.reset(10_000, 1000, CameraView.UNKNOWN, initial())
        val result = requireNotNull(collector.finish(token, 9999, 999, ExerciseType.IDLE, -1))
        assertEquals("UNKNOWN", result.cameraView); assertEquals("UNKNOWN", result.expectedSessionExercise)
        assertEquals(10_000L, result.endedAt); assertEquals(0L, result.activeDurationMs)
    }

    @Test fun concurrentAdmissionsAndCompletionsDoNotLoseAggregates() {
        val token = begin(); val threads = Executors.newFixedThreadPool(4); val done = CountDownLatch(4)
        repeat(4) {
            threads.execute { try { repeat(100) { sample(token) } } finally { done.countDown() } }
        }
        try { assertTrue(done.await(5, TimeUnit.SECONDS)) } finally { threads.shutdownNow() }
        val result = end(token)
        assertEquals(400L, result.totalFramesSeen); assertEquals(400L, result.admittedFrames)
        assertEquals(400L, result.processedFrames); assertEquals(400L, result.matchingStableFrames)
    }
}
