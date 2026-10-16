package com.example.arptapp.domain.detection

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YoloActualGoldenComparisonTest {
    private fun detection(id: Int = 0, score: Float = .9f, offset: Float = 0f) =
        YoloGoldenDetection(id, score, DetectionBox(10f + offset, 20f, 110f + offset, 220f))

    @Test fun exactOutputMatches() {
        val result = YoloActualGoldenComparator.compare(listOf(detection()), listOf(detection()))
        assertTrue(result.passed); assertEquals(1f, result.matches.single().boxIou, 0f)
        assertEquals("ORDER_MATCH", result.ordering.status)
        assertTrue(result.ordering.classSequenceMatches)
    }
    @Test fun unequalScoreNmsReorderingIsNotSilentlyHiddenBySetMatching() {
        val expected = listOf(detection(), detection(1, .85f, 100f))
        val result = YoloActualGoldenComparator.compare(expected, expected.reversed())
        assertFalse(result.passed)
        assertEquals("POST_NMS_ORDER_MISMATCH", result.reason)
        assertEquals("NON_TIE_ORDER_MISMATCH", result.ordering.status)
        assertEquals(listOf(0, 1), result.ordering.referenceClassIds)
        assertEquals(listOf(1, 0), result.ordering.androidClassIds)
        assertEquals(listOf(1, 0), result.ordering.referenceToAndroidIndices)
        assertFalse(result.ordering.classSequenceMatches)
    }
    @Test fun exactScoreTiesCanReorderButRemainExplicitlyReported() {
        val expected = listOf(detection(), detection(1, .9f, 100f))
        val result = YoloActualGoldenComparator.compare(expected, expected.reversed())
        assertTrue(result.passed)
        assertEquals("EQUIVALENT_EXACT_SCORE_TIE_REORDER", result.ordering.status)
        assertFalse(result.ordering.classSequenceMatches)
    }
    @Test fun nearTieIsNotAnExactScoreTieOrderingException() {
        val expected = listOf(detection(), detection(1, .9001f, 100f))
        assertFalse(YoloActualGoldenComparator.compare(expected, expected.reversed()).passed)
    }
    @Test fun exactTieInOnlyOneOutputCannotBypassOrdering() {
        val expected = listOf(detection(), detection(1, .9f, 100f))
        val actual = listOf(detection(1, .9001f, 100f), detection())
        assertFalse(YoloActualGoldenComparator.compare(expected, actual).passed)
    }
    @Test fun ambiguousCloseScoresAndBoxesKeepAlreadyEquivalentIdentityOrdering() {
        val expected = listOf(detection(score = .9001f), detection(score = .9f, offset = 1f))
        val result = YoloActualGoldenComparator.compare(expected, expected)
        assertTrue(result.passed)
        assertEquals("ORDER_MATCH", result.ordering.status)
        assertEquals(listOf(0, 1), result.ordering.referenceToAndroidIndices)
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
    @Test fun noDetectionsCanCompareWithoutFabricatingSemanticSuccess() {
        assertTrue(YoloActualGoldenComparator.compare(emptyList(), emptyList()).passed)
        // A zero-result reference is valid runtime evidence, not evidence of a correctly classified exercise.
    }
    @Test fun samePredictionIsRuntimeEquivalentIndependentlyOfTheImageFilename() {
        assertTrue(YoloActualGoldenComparator.compare(listOf(detection(1)), listOf(detection(1))).passed)
        // Filename/actual exercise labels must not be a comparator input or a semantic acceptance gate.
    }
    @Test fun countMismatchStillReportsRawClassOrdering() {
        val result = YoloActualGoldenComparator.compare(listOf(detection()), emptyList())
        assertFalse(result.passed)
        assertEquals(listOf(0), result.ordering.referenceClassIds)
        assertEquals(emptyList<Int>(), result.ordering.androidClassIds)
    }
    @Test(expected = IllegalArgumentException::class) fun confidenceToleranceCannotBeWidened() { YoloGoldenTolerance(confidence = .01f) }
    @Test(expected = IllegalArgumentException::class) fun boxToleranceCannotBeWidened() { YoloGoldenTolerance(bboxPixels = 3f) }
    @Test(expected = IllegalArgumentException::class) fun invalidToleranceFailsClosed() { YoloGoldenTolerance(bboxPixels = Float.NaN) }

    private fun normalizedTensor() = ByteBuffer.allocate(4 * 3 * 640 * 640).order(ByteOrder.LITTLE_ENDIAN)
    private fun compareTensors(reference: ByteBuffer, actual: ByteBuffer, width: Int = 2, height: Int = 1) =
        YoloActualGoldenTensorComparator.compare(reference, actual, Exp02FrameTransform.create(width, height))

    @Test fun identicalFullTensorsReportZeroErrorWithoutConsumingBorrowedBuffers() {
        val reference = normalizedTensor()
        val actual = normalizedTensor()
        val result = compareTensors(reference, actual)
        assertTrue(result.passed)
        assertEquals("BYTE_IDENTICAL", result.status)
        assertEquals(3 * 640 * 640, result.comparedFloatCount)
        assertEquals(0.0, requireNotNull(result.maximumNormalizedError), 0.0)
        assertEquals(0.0, requireNotNull(result.meanNormalizedError), 0.0)
        assertEquals(0, reference.position())
        assertEquals(0, actual.position())
    }
    @Test fun oneRgbLevelInResampledRegionUsesExistingBoundAndReportsMeasuredError() {
        val reference = normalizedTensor()
        val actual = normalizedTensor().apply { putFloat(4 * 160 * 640, 1f / 255) }
        val result = compareTensors(reference, actual)
        assertTrue(result.passed)
        assertEquals("WITHIN_EXISTING_PIXEL_BOUND", result.status)
        assertEquals((1f / 255).toDouble(), requireNotNull(result.maximumNormalizedError), 0.0)
        assertEquals(1.0, requireNotNull(result.maximumRgbValueEquivalentError), 1e-6)
        assertEquals((1f / 255).toDouble() / (3 * 640 * 640), requireNotNull(result.meanNormalizedError), 1e-12)
        assertEquals(0.0, requireNotNull(result.maximumExactRegionError), 0.0)
    }
    @Test fun twoRgbLevelsInResampledRegionFailWithoutWideningBound() {
        val actual = normalizedTensor().apply { putFloat(4 * 160 * 640, 2f / 255) }
        val result = compareTensors(normalizedTensor(), actual)
        assertFalse(result.passed)
        assertEquals("OUTSIDE_EXISTING_PIXEL_BOUND", result.status)
    }
    @Test fun paddingStillUsesStrictExactPixelBoundWhenFrameIsResized() {
        val actual = normalizedTensor().apply { putFloat(0, 1f / 255) }
        val result = compareTensors(normalizedTensor(), actual)
        assertFalse(result.passed)
        assertTrue(requireNotNull(result.maximumExactRegionError) > 1e-6)
    }
    @Test fun nativeSizeImageHasNoResamplingAllowance() {
        val actual = normalizedTensor().apply { putFloat(4 * 160 * 640, 1f / 255) }
        assertFalse(compareTensors(normalizedTensor(), actual, 640, 640).passed)
    }
    @Test fun invalidNormalizedTensorValueFailsClosed() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 1.01f)) {
            val result = compareTensors(normalizedTensor(), normalizedTensor().apply { putFloat(0, invalid) })
            assertFalse(result.passed)
            assertEquals("INVALID_NORMALIZED_TENSOR", result.status)
        }
    }
    @Test fun wrongTensorSizeFailsBeforeReading() {
        val result = compareTensors(ByteBuffer.allocate(4), ByteBuffer.allocate(4))
        assertFalse(result.passed)
        assertEquals("INVALID_TENSOR_SIZE", result.status)
    }
}
