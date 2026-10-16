package com.example.arptapp.domain.detection

import org.junit.Assert.*
import org.junit.Test

class YoloActualGoldenComparisonTest {
    private fun detection(id: Int = 0, score: Float = .9f, offset: Float = 0f) =
        YoloGoldenDetection(id, score, DetectionBox(10f + offset, 20f, 110f + offset, 220f))

    @Test fun exactOutputMatches() {
        val result = YoloActualGoldenComparator.compare(listOf(detection()), listOf(detection()))
        assertTrue(result.passed); assertEquals(1f, result.matches.single().boxIou, 0f)
    }
    @Test fun nmsOrderingDoesNotMatter() {
        val expected = listOf(detection(), detection(1, .85f, 100f))
        assertTrue(YoloActualGoldenComparator.compare(expected, expected.reversed()).passed)
    }
    @Test fun classMustMatchExactly() {
        assertFalse(YoloActualGoldenComparator.compare(listOf(detection()), listOf(detection(1))).passed)
    }
    @Test fun countMustMatchExactly() {
        assertFalse(YoloActualGoldenComparator.compare(listOf(detection()), emptyList()).passed)
    }
    @Test fun boundedConfidenceAndBoxErrorsMatch() {
        assertTrue(YoloActualGoldenComparator.compare(listOf(detection()), listOf(detection(score = .9005f, offset = 1.5f))).passed)
    }
    @Test fun excessiveConfidenceErrorFails() {
        assertFalse(YoloActualGoldenComparator.compare(listOf(detection()), listOf(detection(score = .902f))).passed)
    }
    @Test fun excessiveBoxErrorFails() {
        assertFalse(YoloActualGoldenComparator.compare(listOf(detection()), listOf(detection(offset = 2.01f))).passed)
    }
    @Test fun duplicateCannotMatchOneReferenceTwice() {
        assertFalse(YoloActualGoldenComparator.compare(listOf(detection(), detection(offset = 50f)), listOf(detection(), detection())).passed)
    }
    @Test fun ambiguousMatchesUseOneToOneAssignmentNotGreedy() {
        val reference = listOf(detection(offset = 0f), detection(offset = 2f))
        val android = listOf(detection(offset = 1f), detection(offset = -1.5f))
        assertTrue(YoloActualGoldenComparator.compare(reference, android).passed)
    }
    @Test fun invalidConfidenceFailsClosed() {
        assertFalse(YoloActualGoldenComparator.compare(listOf(detection()), listOf(detection(score = Float.NaN))).passed)
    }
    @Test fun invalidBoxFailsClosed() {
        val invalid = YoloGoldenDetection(0, .9f, DetectionBox(1f, 1f, 0f, 0f))
        assertFalse(YoloActualGoldenComparator.compare(listOf(invalid), listOf(invalid)).passed)
    }
    @Test fun noDetectionsCanCompareButIsNotAPositiveGoldenSample() {
        assertTrue(YoloActualGoldenComparator.compare(emptyList(), emptyList()).passed)
        // The instrumentation manifest validation separately requires expected-class non-empty samples.
    }
    @Test(expected = IllegalArgumentException::class) fun confidenceToleranceCannotBeWidened() { YoloGoldenTolerance(confidence = .01f) }
    @Test(expected = IllegalArgumentException::class) fun boxToleranceCannotBeWidened() { YoloGoldenTolerance(bboxPixels = 3f) }
    @Test(expected = IllegalArgumentException::class) fun invalidToleranceFailsClosed() { YoloGoldenTolerance(bboxPixels = Float.NaN) }
}
