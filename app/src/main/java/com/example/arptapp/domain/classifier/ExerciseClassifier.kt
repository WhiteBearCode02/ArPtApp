package com.example.arptapp.domain.classifier

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.data.model.Landmark
import kotlin.math.abs

/**
 * A conservative landmark-based fallback classifier.
 *
 * A model-backed implementation can replace this class later without changing the
 * dashboard or Analyzer API. Until then, a type must be observed in consecutive
 * frames before it can select a repetition counter.
 */
class ExerciseClassifier(private val requiredStableFrames: Int = 5) {
    init { require(requiredStableFrames > 0) }
    private var candidate = ExerciseType.UNKNOWN
    private var candidateFrameCount = 0
    private var stableExercise = ExerciseType.UNKNOWN
    private var unknownFrames = 0
    private var lastView: CameraView? = null
    val candidateExercise: ExerciseType get() = candidate
    val stableDetectedExercise: ExerciseType get() = stableExercise

    fun detectExercise(landmarks: List<NormalizedLandmark>, cameraView: CameraView = CameraView.FRONT,
                       imageAspectRatio: Double = 1.0): ExerciseType = detectFeatures(
        ExerciseFeatureExtractor.extract(landmarks.map { Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f)) },
            cameraView, imageAspectRatio), cameraView)

    fun detectFeatures(features: ExerciseFeatures, cameraView: CameraView): ExerciseType {
        if (lastView != null && lastView != cameraView) reset()
        lastView = cameraView
        fun valid(angle: Double?) = angle?.let { it.isFinite() && it in 0.0..180.0 } == true
        fun press(side: ExerciseSideFeatures) = side.armVisibility >= 0.65f && valid(side.elbow) &&
            side.elbow!! >= 55 && side.wristAboveShoulder == true
        fun squat(side: ExerciseSideFeatures) = side.legVisibility >= 0.65f && valid(side.knee) && valid(side.hip) &&
            side.knee!! <= 135 && side.hip!! <= 155
        val (l, r) = features
        val next = when (cameraView) {
            CameraView.FRONT -> when {
                press(l) && press(r) -> ExerciseType.SHOULDER_PRESS
                squat(l) && squat(r) && abs(l.knee!! - r.knee!!) <= 30 -> ExerciseType.SQUAT
                // Unsupported LUNGE is a UI candidate, not allowed to replace a supported stable type.
                l.legVisibility >= 0.65f && r.legVisibility >= 0.65f && valid(l.knee) && valid(r.knee) &&
                    minOf(l.knee!!, r.knee!!) <= 110 && maxOf(l.knee, r.knee) >= 155 &&
                    abs(l.knee - r.knee) >= 45 -> ExerciseType.LUNGE
                else -> ExerciseType.UNKNOWN
            }
            CameraView.SIDE -> {
                val leg = if (l.legVisibility >= r.legVisibility) l else r
                val arm = if (l.armVisibility >= r.armVisibility) l else r
                when { press(arm) -> ExerciseType.SHOULDER_PRESS; squat(leg) -> ExerciseType.SQUAT; else -> ExerciseType.UNKNOWN }
            }
            CameraView.UNKNOWN -> ExerciseType.UNKNOWN
        }
        return stabilize(next)
    }

    private fun stabilize(frameExercise: ExerciseType): ExerciseType {
        if (frameExercise == ExerciseType.UNKNOWN) {
            candidate = ExerciseType.UNKNOWN
            candidateFrameCount = 0
            if (++unknownFrames >= requiredStableFrames * 3) stableExercise = ExerciseType.UNKNOWN
            return stableExercise
        }
        unknownFrames = 0

        if (frameExercise == candidate) {
            candidateFrameCount++
        } else {
            candidate = frameExercise
            candidateFrameCount = 1
        }

        val required = if (stableExercise != ExerciseType.UNKNOWN && stableExercise != candidate)
            requiredStableFrames * 2 else requiredStableFrames
        if (candidateFrameCount >= required && candidate != ExerciseType.LUNGE) stableExercise = candidate
        return stableExercise
    }

    fun reset() {
        candidate = ExerciseType.UNKNOWN
        candidateFrameCount = 0
        stableExercise = ExerciseType.UNKNOWN
        unknownFrames = 0
        lastView = null
    }
}
