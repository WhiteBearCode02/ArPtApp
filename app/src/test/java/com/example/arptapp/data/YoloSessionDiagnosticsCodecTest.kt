package com.example.arptapp.data

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.detection.*
import org.junit.Assert.*
import org.junit.Test

class YoloSessionDiagnosticsCodecTest {
    private fun missing() = YoloSessionDiagnostics(modelStatus = "MODEL_MISSING", contractStatus = "NOT_VERIFIED",
        predictionEnabled = false, startedAt = 1000, endedAt = 2000)
    private fun successful(): YoloSessionDiagnostics {
        val collector = YoloSessionDiagnosticsCollector()
        val snapshot = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
            ExerciseDetection(ExerciseType.SQUAT, .9f, DetectionSource.YOLO), timings = DetectionTimings(2, 8, 3, 13),
            contractVerification = ExportContractVerification.VERIFIED), ExerciseType.SQUAT)
        val token = collector.reset(1000, 100, CameraView.SIDE, snapshot)
        collector.observeFrameSeen(token); collector.observeAdmission(token)
        collector.observeSnapshot(token, snapshot, ExerciseType.SQUAT, 2, 500)
        return requireNotNull(collector.finish(token, 2000, 1100, ExerciseType.SQUAT, 1))
    }

    @Test fun successfulAggregateRoundTripsWithoutAnyFramePayload() {
        val original = successful(); val encoded = requireNotNull(YoloSessionDiagnosticsCodec.encode(original))
        assertEquals(original, YoloSessionDiagnosticsCodec.decode(encoded))
        assertFalse(encoded.contains("boundingBox")); assertFalse(encoded.contains("landmark")); assertFalse(encoded.contains("userId"))
        assertTrue(encoded.length < YoloSessionDiagnosticsCodec.MAX_JSON_CHARS)
    }

    @Test fun modelMissingAndNotVerifiedRoundTripWithNullMetrics() {
        val decoded = requireNotNull(YoloSessionDiagnosticsCodec.decode(YoloSessionDiagnosticsCodec.encode(missing())))
        assertEquals("MODEL_MISSING", decoded.modelStatus); assertEquals(0L, decoded.processedFrames)
        assertEquals(0L, decoded.rawDetectionCounts["UNKNOWN"]); assertNull(decoded.averageConfidence)
        assertNull(decoded.averageProcessedFps); assertFalse(decoded.predictionEnabled)
    }

    @Test fun legacyNullEmptyMalformedAndOversizedAreSafelyAbsent() {
        listOf(null, "", "null", "[]", "{}", "{", " ".repeat(YoloSessionDiagnosticsCodec.MAX_JSON_CHARS + 1))
            .forEach { assertNull(YoloSessionDiagnosticsCodec.decode(it)) }
        assertNull(YoloSessionDiagnosticsCodec.encode(null))
    }

    @Test fun unknownFieldsCannotSmugglePersonalOrFrameDataIntoPersistence() {
        val raw = requireNotNull(YoloSessionDiagnosticsCodec.encode(missing())).dropLast(1) + ",\"image\":\"private-frame\"}"
        assertNull(YoloSessionDiagnosticsCodec.decode(raw))
    }

    @Test fun unknownSchemaModelAndHashAreRejected() {
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(schemaVersion = 2)))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(modelId = "untrusted-model")))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(modelSha256 = "0".repeat(64))))
    }

    @Test fun arbitraryStatusDirectionAndExerciseStringsAreRejected() {
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(modelStatus = "secret exception")))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(contractStatus = "FORCE_VERIFIED")))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(cameraView = "FORTY_FIVE")))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(expectedSessionExercise = "LUNGE")))
    }

    @Test fun predictionRequiresReadyAndVerified() {
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(predictionEnabled = true)))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(modelStatus = "READY", predictionEnabled = true)))
    }

    @Test fun negativeCountsAndInconsistentAdmissionAreRejected() {
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(totalFramesSeen = -1)))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(admittedFrames = 1)))
        assertNull(YoloSessionDiagnosticsCodec.encode(successful().copy(admittedFrames = 0)))
    }

    @Test fun rawAndStableCountKeysAndTotalsAreStrictlyWhitelisted() {
        val value = successful()
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(rawDetectionCounts = mapOf("private-name" to 1))))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(stableDetectionCounts = mapOf("SQUAT" to -1))))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(rawDetectionCounts = emptyMap())))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(stableDetectionCounts = mapOf("SQUAT" to 1, "UNKNOWN" to 1))))
    }

    @Test fun comparisonCountsCannotExceedProcessedAndCannotOverflow() {
        val value = successful()
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(unknownSessionFrames = 1)))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(matchingStableFrames = Long.MAX_VALUE, mismatchingStableFrames = Long.MAX_VALUE)))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(matchingStableFrames = 0)))
    }

    @Test fun timingMetricsMustBeFiniteMeasuredAndNonnegative() {
        assertNull(YoloSessionDiagnosticsCodec.encode(successful().copy(averageTotalMs = Double.NaN)))
        assertNull(YoloSessionDiagnosticsCodec.encode(successful().copy(averageInferenceMs = -1.0)))
        assertNull(YoloSessionDiagnosticsCodec.encode(successful().copy(averagePreprocessMs = null)))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(averageInferenceMs = 0.0)))
    }

    @Test fun confidenceSampleCountsAndRangeAreValidated() {
        val value = successful()
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(confidenceSampleCount = 2)))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(averageConfidence = null)))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(averageConfidence = 2.0)))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(minConfidence = 1.0)))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(averageConfidence = 0.0)))
    }

    @Test fun fpsMustComeFromProcessedCountAndMonotonicDuration() {
        val value = successful()
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(averageProcessedFps = 30.0)))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(averageProcessedFps = Double.POSITIVE_INFINITY)))
        assertNull(YoloSessionDiagnosticsCodec.encode(value.copy(activeDurationMs = 0)))
        assertNotNull(YoloSessionDiagnosticsCodec.encode(value.copy(activeDurationMs = 0, averageProcessedFps = null)))
    }

    @Test fun invalidThermalAndReversedEpochAreRejected() {
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(maxThermalStatus = 7)))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(maxThermalStatus = -1)))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(endedAt = 999)))
        assertNull(YoloSessionDiagnosticsCodec.encode(missing().copy(startedAt = -1)))
    }

    @Test fun allCurrentFailureStatusesRemainReadable() {
        DetectorStatus.entries.filter { it != DetectorStatus.READY }.forEach { status ->
            val value = missing().copy(modelStatus = status.name)
            assertNotNull("Status ${status.name}", YoloSessionDiagnosticsCodec.decode(YoloSessionDiagnosticsCodec.encode(value)))
        }
    }
}
