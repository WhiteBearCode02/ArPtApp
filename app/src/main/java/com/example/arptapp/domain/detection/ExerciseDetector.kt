package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType

enum class DetectionSource { YOLO, LANDMARK_FALLBACK }
enum class DetectorStatus {
    INITIALIZING, READY, CONTRACT_NOT_VERIFIED, MODEL_MISSING, TENSOR_MISMATCH,
    INITIALIZATION_FAILED, INFERENCE_FAILED, INVALID_OUTPUT, TIMED_OUT, CLOSED
}

/** Canonical source-frame pixel box, NOT a claim about EXP02 raw output channel semantics. */
data class DetectionBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val isValid: Boolean get() = listOf(left, top, right, bottom).all { it.isFinite() } && right > left && bottom > top
    val area: Float get() = if (isValid) (right - left) * (bottom - top) else 0f
}
data class DetectionTimings(val preprocessMs: Long, val inferenceMs: Long, val postprocessMs: Long, val totalMs: Long)
data class ExerciseDetection(
    val exerciseType: ExerciseType, val confidence: Float?, val source: DetectionSource,
    val boundingBox: DetectionBox? = null
)
/** In-memory diagnostics only. No image, account, persisted box, or report/upload DTO. */
data class ExerciseDetectionResult(
    val status: DetectorStatus, val source: DetectionSource, val selected: ExerciseDetection? = null,
    val detectionCount: Int = 0, val timings: DetectionTimings? = null
) {
    val exerciseType: ExerciseType get() = selected?.exerciseType ?: ExerciseType.UNKNOWN
}

/** A replaceable inference port; callers must not route shadow output into SessionExerciseLock. */
interface ExerciseDetector<F> : AutoCloseable {
    fun initialize(): ExerciseDetectionResult
    fun detect(frame: F): ExerciseDetectionResult
}

/** Same largest-area philosophy as MainPersonSelector; NOT bbox-to-pose identity association. */
object MainExerciseDetectionSelector {
    fun select(detections: List<ExerciseDetection>): ExerciseDetection? = detections
        .filter { it.exerciseType in YoloModelPolicy.supported && it.confidence?.let { c -> c.isFinite() && c in 0f..1f } == true &&
            it.boundingBox?.isValid != false }
        .maxWithOrNull(compareBy<ExerciseDetection> { it.boundingBox?.area ?: 0f }.thenBy { it.confidence ?: 0f })
}
