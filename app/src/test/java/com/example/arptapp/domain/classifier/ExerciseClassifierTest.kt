package com.example.arptapp.domain.classifier

import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseClassifierTest {
    @Test
    fun switchesExerciseOnlyAfterStableLandmarkEvidence() {
        val classifier = ExerciseClassifier(requiredStableFrames = 3)

        repeat(2) { assertEquals(ExerciseType.UNKNOWN, classifier.classifyAngles(100f, 100f)) }
        assertEquals(ExerciseType.SQUAT, classifier.classifyAngles(100f, 100f))

        repeat(2) { assertEquals(ExerciseType.SQUAT, classifier.classifyAngles(100f, 170f)) }
        assertEquals(ExerciseType.LUNGE, classifier.classifyAngles(100f, 170f))
    }
}
