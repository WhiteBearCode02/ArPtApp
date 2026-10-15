package com.example.arptapp.domain.detection

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.viewmodel.MainViewModel
import org.junit.Assert.*
import org.junit.Test

class YoloStabilizerTest {
    private fun detection(type: ExerciseType = ExerciseType.SQUAT, confidence: Float = .9f) = ExerciseDetection(type, confidence, DetectionSource.YOLO)
    @Test fun fourAgreeingSquatsProduceStableSquat() {
        val stabilizer = YoloExerciseStabilizer(YoloStabilizationPolicy(.7f))
        repeat(3) { assertEquals(ExerciseType.UNKNOWN, stabilizer.observe(detection(), it * 250L)) }
        assertEquals(ExerciseType.SQUAT, stabilizer.observe(detection(), 750))
    }
    @Test fun repeatedPressIsSupported() {
        val stabilizer = YoloExerciseStabilizer(YoloStabilizationPolicy(.7f))
        repeat(4) { stabilizer.observe(detection(ExerciseType.SHOULDER_PRESS), it * 250L) }
        assertEquals(ExerciseType.SHOULDER_PRESS, stabilizer.observe(detection(ExerciseType.SHOULDER_PRESS), 1000))
    }
    @Test fun oneTransientWrongClassDoesNotReplaceMajority() {
        val stabilizer = YoloExerciseStabilizer(YoloStabilizationPolicy(.7f))
        listOf(ExerciseType.SQUAT, ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS, ExerciseType.SQUAT, ExerciseType.SQUAT)
            .forEachIndexed { i, type -> stabilizer.observe(detection(type), i * 250L) }
        assertEquals(ExerciseType.SQUAT, stabilizer.observe(detection(), 1250))
    }
    @Test fun lowConfidenceAndAlternatingClassesRemainUnknown() {
        val low = YoloExerciseStabilizer(YoloStabilizationPolicy(.7f))
        val alternating = YoloExerciseStabilizer(YoloStabilizationPolicy(.7f))
        repeat(20) { i ->
            assertEquals(ExerciseType.UNKNOWN, low.observe(detection(confidence = .1f), i * 250L))
            assertEquals(ExerciseType.UNKNOWN, alternating.observe(detection(if (i % 2 == 0) ExerciseType.SQUAT else ExerciseType.SHOULDER_PRESS), i * 250L))
        }
    }
    @Test fun absentConfidencePolicyAlwaysAbstains() {
        val stabilizer = YoloExerciseStabilizer(YoloStabilizationPolicy(null))
        repeat(20) { assertEquals(ExerciseType.UNKNOWN, stabilizer.observe(detection(confidence = 1f), it * 250L)) }
    }
    @Test fun staleEvidenceAndClockReversalAreDiscarded() {
        val stabilizer = YoloExerciseStabilizer(YoloStabilizationPolicy(.7f))
        repeat(4) { stabilizer.observe(detection(), it * 250L) }
        assertEquals(ExerciseType.UNKNOWN, stabilizer.observe(null, 4000))
        assertEquals(ExerciseType.UNKNOWN, stabilizer.observe(detection(), 100))
    }
    @Test fun majorityExpiresWithMissingDetections() {
        val stabilizer = YoloExerciseStabilizer(YoloStabilizationPolicy(.7f))
        repeat(5) { stabilizer.observe(detection(), it * 250L) }
        assertEquals(ExerciseType.SQUAT, stabilizer.observe(null, 1250))
        assertEquals(ExerciseType.UNKNOWN, stabilizer.observe(null, 1500))
    }
    @Test fun diagnosticComparisonCannotChangeLockedSession() {
        val vm = MainViewModel()
        vm.observeDetectedExercise(ExerciseType.SQUAT)
        val yolo = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
            detection(ExerciseType.SHOULDER_PRESS)), ExerciseType.SHOULDER_PRESS)
        val text = DetectorComparisonFormatter.format(DetectorComparison(yolo, ExerciseType.SQUAT,
            vm.sessionExerciseType, CameraView.SIDE, 2, 3))
        assertTrue(text.contains("raw=SHOULDER_PRESS"))
        assertTrue(text.contains("fallback=SQUAT · session=SQUAT · view=SIDE"))
        assertTrue(text.contains("MULTI_PERSON_UNASSOCIATED"))
        assertEquals(ExerciseType.SQUAT, vm.sessionExerciseType)
    }
    @Test fun unavailableDiagnosticsDoNotInventLatencyOrConfidence() {
        val text = DetectorComparisonFormatter.format(DetectorComparison(
            YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.CONTRACT_NOT_VERIFIED, DetectionSource.YOLO)),
            ExerciseType.UNKNOWN, ExerciseType.UNKNOWN, CameraView.UNKNOWN, 0, -1))
        assertTrue(text.contains("NOT_VERIFIED")); assertTrue(text.contains("미측정"))
        assertFalse(text.contains("113")); assertFalse(text.contains("NaN"))
    }
    @Test fun canonicalSelectionUsesLargestAreaThenConfidence() {
        val small = detection().copy(boundingBox = DetectionBox(0f, 0f, 10f, 10f))
        val large = detection(ExerciseType.SHOULDER_PRESS, .8f).copy(boundingBox = DetectionBox(0f, 0f, 20f, 20f))
        assertEquals(large, MainExerciseDetectionSelector.select(listOf(small, large)))
        assertNull(MainExerciseDetectionSelector.select(listOf(small.copy(boundingBox = DetectionBox(Float.NaN, 0f, 1f, 1f)))))
    }
}
