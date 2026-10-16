package com.example.arptapp.domain.detection

import java.nio.ByteBuffer
import kotlin.math.abs

/** Validation-only final post-NMS comparison. Never referenced by the camera's verification gate. */
data class YoloGoldenDetection(val classId: Int, val confidence: Float, val box: DetectionBox)

/**
 * Provisional engineering acceptance limits, NOT measured device accuracy or permission to verify.
 * Never increase these automatically after a failure. Count and class identity must match exactly.
 */
data class YoloGoldenTolerance(val confidence: Float = .001f, val bboxPixels: Float = 2f) {
    init {
        require(confidence.isFinite() && confidence >= 0 && confidence <= .001f)
        require(bboxPixels.isFinite() && bboxPixels >= 0 && bboxPixels <= 2f)
    }
}

data class YoloGoldenMatch(
    val referenceIndex: Int, val androidIndex: Int, val confidenceError: Float,
    val maximumBoxErrorPixels: Float, val boxIou: Float
)
/** Raw class sequences remain observable even when equal-confidence NMS output is reordered. */
data class YoloGoldenOrdering(
    val referenceClassIds: List<Int>, val androidClassIds: List<Int>, val classSequenceMatches: Boolean,
    val referenceToAndroidIndices: List<Int> = emptyList(), val status: String = "NOT_COMPARED"
)
data class YoloGoldenComparison(
    val passed: Boolean, val reason: String, val matches: List<YoloGoldenMatch> = emptyList(),
    val ordering: YoloGoldenOrdering
)

/**
 * Validation evidence using the existing synthetic Exp02ContractTest interpolation bounds, not a
 * newly tuned allowance: exact/padding 1e-6, resampled RGB at most one uint8 level plus 1e-6.
 * Values multiplied by 255 are RGB-value-equivalent errors, NOT a claim about integer tensors.
 */
data class YoloGoldenTensorComparison(
    val passed: Boolean, val status: String, val comparedFloatCount: Int,
    val maximumNormalizedError: Double? = null, val meanNormalizedError: Double? = null,
    val maximumRgbValueEquivalentError: Double? = null, val meanRgbValueEquivalentError: Double? = null,
    val maximumExactRegionError: Double? = null, val maximumResampledRegionError: Double? = null,
    val exactNormalizedTolerance: Double = 1e-6, val resampledNormalizedTolerance: Double = 1.0 / 255 + 1e-6
)

object YoloActualGoldenTensorComparator {
    fun compare(reference: ByteBuffer, android: ByteBuffer, transform: Exp02FrameTransform): YoloGoldenTensorComparison {
        if (reference.remaining() != 4 * 3 * 640 * 640 || reference.remaining() != android.remaining())
            return YoloGoldenTensorComparison(false, "INVALID_TENSOR_SIZE", 0)
        val expected = reference.duplicate().order(reference.order())
        val actual = android.duplicate().order(android.order())
        val count = expected.remaining() / 4
        var maximum = 0.0
        var sum = 0.0
        var maximumExact = 0.0
        var maximumResampled = 0.0
        var rawBitsIdentical = reference.order() == android.order()
        val resized = transform.resizedWidth != transform.originalWidth || transform.resizedHeight != transform.originalHeight
        repeat(count) {
            val expectedValue = expected.float
            val actualValue = actual.float
            if (!expectedValue.isFinite() || !actualValue.isFinite() ||
                expectedValue !in 0f..1f || actualValue !in 0f..1f)
                return YoloGoldenTensorComparison(false, "INVALID_NORMALIZED_TENSOR", it)
            val error = abs(expectedValue.toDouble() - actualValue.toDouble())
            rawBitsIdentical = rawBitsIdentical && expectedValue.toRawBits() == actualValue.toRawBits()
            maximum = maxOf(maximum, error)
            sum += error
            val x = it % 640
            val y = it % (640 * 640) / 640
            val inResampledRegion = resized && x in transform.padX until transform.padX + transform.resizedWidth &&
                y in transform.padY until transform.padY + transform.resizedHeight
            if (inResampledRegion) maximumResampled = maxOf(maximumResampled, error)
            else maximumExact = maxOf(maximumExact, error)
        }
        val mean = sum / count
        val passed = maximumExact <= 1e-6 && maximumResampled <= 1.0 / 255 + 1e-6
        return YoloGoldenTensorComparison(passed, when {
            rawBitsIdentical -> "BYTE_IDENTICAL"
            passed -> "WITHIN_EXISTING_PIXEL_BOUND"
            else -> "OUTSIDE_EXISTING_PIXEL_BOUND"
        }, count, maximum, mean, maximum * 255.0, mean * 255.0, maximumExact, maximumResampled)
    }
}

object YoloActualGoldenComparator {
    /**
     * Exact post-NMS count; ONE-TO-ONE matching. Bipartite matching rather than
     * greedy nearest-box pairing prevents duplicate/ambiguous detections being accepted incorrectly.
     * IoU is reported as evidence; the acceptance condition is the stricter explicit four pixel
     * coordinate errors, score tolerance and exact class identity, not an adjustable broad IoU gate.
     * Reference/Android ordering is independently reported. Only exact-score ties in BOTH outputs
     * may reorder; unequal-score reordering fails instead of being hidden by set equivalence.
     */
    fun compare(
        reference: List<YoloGoldenDetection>, android: List<YoloGoldenDetection>,
        tolerance: YoloGoldenTolerance = YoloGoldenTolerance()
    ): YoloGoldenComparison {
        val referenceClasses = reference.map { it.classId }
        val androidClasses = android.map { it.classId }
        val ordering = YoloGoldenOrdering(referenceClasses, androidClasses, referenceClasses == androidClasses)
        fun valid(detection: YoloGoldenDetection) = detection.classId in YoloModelPolicy.classMapping &&
            detection.confidence.isFinite() && detection.confidence in 0f..1f && detection.box.isValid
        if (reference.size > 300 || android.size > 300 || (reference + android).any { !valid(it) })
            return YoloGoldenComparison(false, "INVALID_DETECTION", ordering = ordering)
        if (reference.size != android.size) return YoloGoldenComparison(false, "POST_NMS_COUNT_MISMATCH", ordering = ordering)
        fun boxError(a: DetectionBox, b: DetectionBox) = maxOf(
            abs(a.left - b.left), abs(a.top - b.top), abs(a.right - b.right), abs(a.bottom - b.bottom))
        val candidates = reference.map { expected -> android.indices.filter { index ->
            val actual = android[index]
            expected.classId == actual.classId &&
                abs(expected.confidence - actual.confidence) <= tolerance.confidence &&
                boxError(expected.box, actual.box) <= tolerance.bboxPixels
        } }
        fun match(edges: List<List<Int>>): IntArray? {
            // Exact index alignment wins before a generic augmenting path can unnecessarily
            // reverse two close/ambiguous detections that are already identical in order.
            if (edges.indices.all { it in edges[it] }) return IntArray(android.size) { it }
            val owner = IntArray(android.size) { -1 }
            fun assign(referenceIndex: Int, visited: BooleanArray): Boolean {
                for (androidIndex in edges[referenceIndex]) {
                    if (visited[androidIndex]) continue
                    visited[androidIndex] = true
                    if (owner[androidIndex] == -1 || assign(owner[androidIndex], visited)) {
                        owner[androidIndex] = referenceIndex
                        return true
                    }
                }
                return false
            }
            for (index in reference.indices) if (!assign(index, BooleanArray(android.size))) return null
            return owner
        }
        // An admissible permutation cannot cross a confidence boundary in EITHER output.
        // Try only those exact-tie blocks first; unconstrained matching is diagnostic fallback
        // for an actual non-tie ordering mismatch, never a way to pass such a mismatch.
        val tieBlocks = IntArray(reference.size)
        for (index in 1 until reference.size) {
            tieBlocks[index] = tieBlocks[index - 1] + if (
                reference[index].confidence == reference[index - 1].confidence &&
                android[index].confidence == android[index - 1].confidence) 0 else 1
        }
        val orderedCandidates = candidates.mapIndexed { referenceIndex, indices ->
            indices.filter { tieBlocks[referenceIndex] == tieBlocks[it] }
        }
        val owner = match(orderedCandidates) ?: match(candidates)
            ?: return YoloGoldenComparison(false, "CLASS_SCORE_OR_BOX_MISMATCH", ordering = ordering)
        val matches = owner.mapIndexed { androidIndex, referenceIndex ->
            val expected = reference[referenceIndex]; val actual = android[androidIndex]
            YoloGoldenMatch(referenceIndex, androidIndex, abs(expected.confidence - actual.confidence),
                boxError(expected.box, actual.box), Exp02PostProcessor.iou(expected.box, actual.box))
        }.sortedBy { it.referenceIndex }
        val indices = matches.map { it.androidIndex }
        val ordered = indices == reference.indices.toList()
        val onlyExactTiesReordered = matches.indices.all { first -> (first + 1 until matches.size).all { second ->
            val firstAndroid = indices[first]
            val secondAndroid = indices[second]
            firstAndroid < secondAndroid || (reference[first].confidence == reference[second].confidence &&
                android[firstAndroid].confidence == android[secondAndroid].confidence)
        } }
        val orderStatus = when {
            ordered -> "ORDER_MATCH"
            onlyExactTiesReordered -> "EQUIVALENT_EXACT_SCORE_TIE_REORDER"
            else -> "NON_TIE_ORDER_MISMATCH"
        }
        val orderResult = ordering.copy(referenceToAndroidIndices = indices, status = orderStatus)
        return YoloGoldenComparison(ordered || onlyExactTiesReordered,
            if (ordered || onlyExactTiesReordered) "POST_NMS_EQUIVALENT" else "POST_NMS_ORDER_MISMATCH", matches, orderResult)
    }
}
