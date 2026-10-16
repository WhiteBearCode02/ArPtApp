package com.example.arptapp.domain.detection

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
data class YoloGoldenComparison(val passed: Boolean, val reason: String, val matches: List<YoloGoldenMatch> = emptyList())

object YoloActualGoldenComparator {
    /**
     * Exact post-NMS count; order-independent ONE-TO-ONE matching. Bipartite matching rather than
     * greedy nearest-box pairing prevents duplicate/ambiguous detections being accepted incorrectly.
     * IoU is reported as evidence; the acceptance condition is the stricter explicit four pixel
     * coordinate errors, score tolerance and exact class identity, not an adjustable broad IoU gate.
     */
    fun compare(
        reference: List<YoloGoldenDetection>, android: List<YoloGoldenDetection>,
        tolerance: YoloGoldenTolerance = YoloGoldenTolerance()
    ): YoloGoldenComparison {
        fun valid(detection: YoloGoldenDetection) = detection.classId in YoloModelPolicy.classMapping &&
            detection.confidence.isFinite() && detection.confidence in 0f..1f && detection.box.isValid
        if (reference.size > 300 || android.size > 300 || (reference + android).any { !valid(it) })
            return YoloGoldenComparison(false, "INVALID_DETECTION")
        if (reference.size != android.size) return YoloGoldenComparison(false, "POST_NMS_COUNT_MISMATCH")
        fun boxError(a: DetectionBox, b: DetectionBox) = maxOf(
            abs(a.left - b.left), abs(a.top - b.top), abs(a.right - b.right), abs(a.bottom - b.bottom))
        val candidates = reference.map { expected -> android.indices.filter { index ->
            val actual = android[index]
            expected.classId == actual.classId &&
                abs(expected.confidence - actual.confidence) <= tolerance.confidence &&
                boxError(expected.box, actual.box) <= tolerance.bboxPixels
        } }
        val owner = IntArray(android.size) { -1 }
        fun assign(referenceIndex: Int, visited: BooleanArray): Boolean {
            for (androidIndex in candidates[referenceIndex]) {
                if (visited[androidIndex]) continue
                visited[androidIndex] = true
                if (owner[androidIndex] == -1 || assign(owner[androidIndex], visited)) {
                    owner[androidIndex] = referenceIndex
                    return true
                }
            }
            return false
        }
        for (index in reference.indices) {
            if (!assign(index, BooleanArray(android.size))) return YoloGoldenComparison(false, "CLASS_SCORE_OR_BOX_MISMATCH")
        }
        val matches = owner.mapIndexed { androidIndex, referenceIndex ->
            val expected = reference[referenceIndex]; val actual = android[androidIndex]
            YoloGoldenMatch(referenceIndex, androidIndex, abs(expected.confidence - actual.confidence),
                boxError(expected.box, actual.box), Exp02PostProcessor.iou(expected.box, actual.box))
        }.sortedBy { it.referenceIndex }
        return YoloGoldenComparison(true, "POST_NMS_EQUIVALENT", matches)
    }
}
