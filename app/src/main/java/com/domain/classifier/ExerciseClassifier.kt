package com.example.arptapp.domain.classifier

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.example.arptapp.domain.analyzer.PoseAngleExtractor

/**
 * A conservative landmark-based fallback classifier.
 *
 * A model-backed implementation can replace this class later without changing the
 * dashboard or Analyzer API. Until then, a type must be observed in consecutive
 * frames before it can select a repetition counter.
 */
class ExerciseClassifier(private val requiredStableFrames: Int = 5) {
    private var candidate = ExerciseType.UNKNOWN
    private var candidateFrameCount = 0
    private var stableExercise = ExerciseType.UNKNOWN

    fun detectExercise(landmarks: List<NormalizedLandmark>): ExerciseType {
        val angles = PoseAngleExtractor.extractSquatAngles(landmarks) ?: return stableExercise
        return classifyAngles(angles[0], angles[1])
    }

    internal fun classifyAngles(leftKneeAngle: Float, rightKneeAngle: Float): ExerciseType {
        val frameExercise = when {
            leftKneeAngle <= 115f && rightKneeAngle >= 145f -> ExerciseType.LUNGE
            rightKneeAngle <= 115f && leftKneeAngle >= 145f -> ExerciseType.LUNGE
            leftKneeAngle <= 130f && rightKneeAngle <= 130f -> ExerciseType.SQUAT
            else -> ExerciseType.UNKNOWN
        }

        if (frameExercise == ExerciseType.UNKNOWN) return stableExercise

        if (frameExercise == candidate) {
            candidateFrameCount++
        } else {
            candidate = frameExercise
            candidateFrameCount = 1
        }

        if (candidateFrameCount >= requiredStableFrames) stableExercise = candidate
        return stableExercise
    }

    fun reset() {
        candidate = ExerciseType.UNKNOWN
        candidateFrameCount = 0
        stableExercise = ExerciseType.UNKNOWN
    }
}
