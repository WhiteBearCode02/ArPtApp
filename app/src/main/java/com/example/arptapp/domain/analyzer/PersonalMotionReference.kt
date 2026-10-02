package com.example.arptapp.domain.analyzer

/** A user's comparison reference, never an expert-verified correct posture. */
data class PersonalMotionReference(val exerciseType: String, val cameraView: CameraView, val frames: List<List<Float>>, val version: Int = 1) {
    fun isValid(): Boolean {
        val dimensions = when (exerciseType) { "SQUAT" -> 4; "SHOULDER_PRESS" -> 2; else -> return false }
        if (version != 1 || cameraView == CameraView.UNKNOWN || frames.size !in 15..300 ||
            frames.any { it.size != dimensions || it.any { angle -> !angle.isFinite() || angle !in 0f..180f } }) return false
        // Minimum motion span is a capture-quality check, not a clinical ROM threshold.
        return frames.maxOf { it[0] } - frames.minOf { it[0] } >= 20f
    }
    fun sequence(): List<FloatArray> = if (isValid()) frames.map { it.toFloatArray() } else emptyList()
}
