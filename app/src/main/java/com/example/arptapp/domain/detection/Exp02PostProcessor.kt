package com.example.arptapp.domain.detection

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/** Reference settings and provisional app settings are intentionally different, never a measured optimum. */
object Exp02DetectionPolicy {
    const val REFERENCE_PREDICT_CONFIDENCE = .25f
    const val HISTORICAL_VAL_DEFAULT_CONFIDENCE = .001f
    const val APP_ENGINEERING_INITIAL_RAW_CONFIDENCE = .10f
    const val APP_ENGINEERING_INITIAL_STABLE_CONFIDENCE = .60f
    const val IOU_THRESHOLD = .70f
    const val MAX_DETECTIONS = 300
    const val ANCHORS = 8400
}

/** Exact 8.4.176 raw head/LiteRT path: normalized cx,cy,w,h; already-sigmoid class0,class1. */
class Exp02OutputDecoder : YoloOutputDecoder {
    override fun decode(output: ByteBuffer, transform: YoloFrameTransform): YoloDecodedOutput {
        if (output.remaining() != 4 * 6 * Exp02DetectionPolicy.ANCHORS || output.order() != ByteOrder.nativeOrder()) throw InvalidYoloOutput()
        val tensor = output.slice().order(ByteOrder.nativeOrder())
        val candidates = ArrayList<YoloDecodedCandidate>()
        for (anchor in 0 until Exp02DetectionPolicy.ANCHORS) {
            fun read(channel: Int) = tensor.getFloat((channel * Exp02DetectionPolicy.ANCHORS + anchor) * 4)
            val class0 = read(4); val class1 = read(5)
            if (!class0.isFinite() || !class1.isFinite() || class0 !in 0f..1f || class1 !in 0f..1f) throw InvalidYoloOutput()
            val raw = floatArrayOf(read(0), read(1), read(2), read(3))
            if (raw.any { !it.isFinite() }) throw InvalidYoloOutput()
            val score = max(class0, class1)
            if (score <= 0 || raw[2] <= 0 || raw[3] <= 0) continue // invalid/empty candidates are not forced into a class
            // LiteRTBackend.forward denormalizes; no objectness term and NO second sigmoid.
            val centerX = raw[0] * transform.modelWidth; val centerY = raw[1] * transform.modelHeight
            val halfWidth = raw[2] * transform.modelWidth / 2f; val halfHeight = raw[3] * transform.modelHeight / 2f
            val box = DetectionBox(centerX - halfWidth, centerY - halfHeight, centerX + halfWidth, centerY + halfHeight)
            if (!box.isValid) throw InvalidYoloOutput()
            candidates += YoloDecodedCandidate(anchor, if (class0 >= class1) 0 else 1, score, box)
        }
        return YoloDecodedOutput(candidates, transform)
    }
}

/** Class-aware NMS on UNCLIPPED model pixels first; inverse transform and clipping only afterwards. */
class Exp02PostProcessor(
    private val confidenceThreshold: Float = Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_RAW_CONFIDENCE,
    private val iouThreshold: Float = Exp02DetectionPolicy.IOU_THRESHOLD,
    private val maxDetections: Int = Exp02DetectionPolicy.MAX_DETECTIONS
) : YoloPostProcessor {
    init { require(confidenceThreshold.isFinite() && confidenceThreshold in 0f..1f)
        require(iouThreshold.isFinite() && iouThreshold in 0f..1f); require(maxDetections in 1..300) }
    override fun process(decoded: YoloDecodedOutput): List<ExerciseDetection> {
        if (decoded.candidates.size > Exp02DetectionPolicy.ANCHORS || decoded.candidates.any {
                it.classId !in YoloModelPolicy.classMapping || !it.confidence.isFinite() || it.confidence !in 0f..1f || !it.modelBox.isValid }) throw InvalidYoloOutput()
        val ordered = decoded.candidates.filter { it.confidence > confidenceThreshold }
            .sortedWith(compareByDescending<YoloDecodedCandidate> { it.confidence }.thenBy { it.anchorIndex })
        val kept = ArrayList<YoloDecodedCandidate>()
        for (candidate in ordered) {
            if (kept.none { it.classId == candidate.classId && iou(it.modelBox, candidate.modelBox) > iouThreshold }) kept += candidate
            if (kept.size >= maxDetections) break
        }
        return kept.mapNotNull { candidate ->
            val sourceBox = decoded.transform.modelToOriginal(candidate.modelBox)
            // Additional domain safety guard: fully clipped/degenerate boxes are not usable detections.
            sourceBox.takeIf { it.isValid }?.let { ExerciseDetection(YoloModelPolicy.classMapping.getValue(candidate.classId),
                candidate.confidence, DetectionSource.YOLO, it) }
        }
    }
    companion object {
        fun iou(a: DetectionBox, b: DetectionBox): Float {
            val intersection = max(0f, min(a.right, b.right) - max(a.left, b.left)) *
                max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
            val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - intersection
            if (union > 0 && union.isFinite()) return intersection / union
            // Two individually finite Float areas can overflow when added. Keep normal Float
            // reference arithmetic, but never treat that overflow as "no overlap".
            val overlap = max(0.0, min(a.right.toDouble(), b.right.toDouble()) - max(a.left.toDouble(), b.left.toDouble())) *
                max(0.0, min(a.bottom.toDouble(), b.bottom.toDouble()) - max(a.top.toDouble(), b.top.toDouble()))
            val areaA = (a.right.toDouble() - a.left) * (a.bottom.toDouble() - a.top)
            val areaB = (b.right.toDouble() - b.left) * (b.bottom.toDouble() - b.top)
            val denominator = areaA + areaB - overlap
            return if (denominator > 0 && denominator.isFinite()) (overlap / denominator).toFloat() else 0f
        }
    }
}
