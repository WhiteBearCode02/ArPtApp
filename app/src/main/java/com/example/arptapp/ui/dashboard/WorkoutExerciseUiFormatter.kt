package com.example.arptapp.ui.dashboard

import com.example.arptapp.domain.classifier.ExerciseType

/** Immutable label projection only; never changes a lock, counter, score, reference or detector. */
data class WorkoutExerciseUiState(val detectedText: String, val title: String?, val countUnit: String?)

object WorkoutExerciseUiFormatter {
    fun format(isExercising: Boolean, session: ExerciseType, detected: ExerciseType): WorkoutExerciseUiState {
        val display = if (detected == ExerciseType.IDLE) ExerciseType.UNKNOWN.displayName else detected.displayName
        val locked = isExercising && session in ExerciseType.supported
        return WorkoutExerciseUiState(
            detectedText = if (locked) "세션: ${session.displayName} · 감지: $display" else "인식 중: $display",
            title = if (locked) "${session.displayName} 운동 중" else null,
            countUnit = if (locked) session.displayName else null
        )
    }
}
