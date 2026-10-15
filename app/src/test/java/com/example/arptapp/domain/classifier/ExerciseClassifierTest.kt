package com.example.arptapp.domain.classifier

import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.arptapp.domain.analyzer.CameraView

class ExerciseClassifierTest {
    private fun ExerciseClassifier.classifyAngles(left: Float, right: Float) = detectFeatures(ExerciseFeatures(
        ExerciseSideFeatures(knee = left.toDouble(), hip = 100.0, legVisibility = 1f),
        ExerciseSideFeatures(knee = right.toDouble(), hip = 100.0, legVisibility = 1f)), CameraView.FRONT)
    @Test
    fun unknownFrameBreaksConsecutiveEvidence() {
        val classifier = ExerciseClassifier(requiredStableFrames = 3)
        repeat(2) { classifier.classifyAngles(100f, 100f) }
        classifier.classifyAngles(170f, 170f)
        assertEquals(ExerciseType.UNKNOWN, classifier.classifyAngles(100f, 100f))
    }
    @Test
    fun unsupportedLungeCandidateNeverReplacesStableSupportedExercise() {
        val classifier = ExerciseClassifier(requiredStableFrames = 3)

        repeat(2) { assertEquals(ExerciseType.UNKNOWN, classifier.classifyAngles(100f, 100f)) }
        assertEquals(ExerciseType.SQUAT, classifier.classifyAngles(100f, 100f))

        repeat(2) { assertEquals(ExerciseType.SQUAT, classifier.classifyAngles(100f, 170f)) }
        assertEquals(ExerciseType.SQUAT, classifier.classifyAngles(100f, 170f))
        assertEquals(ExerciseType.LUNGE, classifier.candidateExercise)
    }
}
