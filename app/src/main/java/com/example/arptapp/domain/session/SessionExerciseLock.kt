package com.example.arptapp.domain.session

import com.example.arptapp.domain.classifier.ExerciseType

/** A session contains ONE supported exercise. Detection noise cannot reset counters/history/score context. */
class SessionExerciseLock {
    var sessionExerciseType = ExerciseType.UNKNOWN
        private set
    fun observe(stableDetectedExercise: ExerciseType): Boolean {
        if (sessionExerciseType != ExerciseType.UNKNOWN || stableDetectedExercise !in supported) return false
        sessionExerciseType = stableDetectedExercise
        return true
    }
    fun reset() { sessionExerciseType = ExerciseType.UNKNOWN }
    companion object { val supported = setOf(ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS) }
}
