package com.example.arptapp.ui.camera.yolo.validation

import android.graphics.Bitmap

/** Variant-neutral port only. Release factory is null; no image persistence or inference lives here. */
interface Exp02PositiveValidation : AutoCloseable {
    /** Explicit one-shot request only. Copies while the borrowed camera bitmap is still valid. */
    fun saveAndAnalyze(frame: Bitmap, callback: (PositiveCandidateAnalysis) -> Unit): Boolean
    fun list(callback: (PositiveCandidateInventory) -> Unit): Boolean
    /** Caller must obtain explicit confirmation. Deletes owned candidate files, never models/records. */
    fun deleteAll(callback: (PositiveCandidateInventory) -> Unit): Boolean
}

data class PositiveCandidate(
    val id: String,
    val privatePath: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val sha256: String
)

data class PositiveCandidateDetection(
    val classId: Int,
    val className: String,
    val confidence: Float,
    val bbox: PositiveCandidateBox
)

data class PositiveCandidateBox(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

data class PositiveCandidateAnalysis(
    val candidate: PositiveCandidate?,
    val status: String,
    val nativeInferenceExecuted: Boolean,
    val detections: List<PositiveCandidateDetection>,
    val message: String
)

data class PositiveCandidateInventory(
    val candidates: List<PositiveCandidate>,
    val status: String,
    val deletedFiles: Int = 0
)
