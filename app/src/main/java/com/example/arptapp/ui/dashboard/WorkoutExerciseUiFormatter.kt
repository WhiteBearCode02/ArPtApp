package com.example.arptapp.ui.dashboard

import com.example.arptapp.domain.classifier.ExerciseType

/** Immutable label projection only; never changes a lock, counter, score, reference or detector. */
data class WorkoutExerciseUiState(val detectedText: String, val title: String?, val countUnit: String?)

object WorkoutExerciseUiFormatter {
    /** Live dashboard policy: automatic predictions are available only in DEBUG diagnostics. */
    fun formatManualSession(isExercising: Boolean, selected: ExerciseType): WorkoutExerciseUiState {
        val active = isExercising && selected in ExerciseType.supported
        return WorkoutExerciseUiState(
            detectedText = if (active) "선택 운동: ${selected.displayName}" else "운동 시작을 눌러 종목을 선택해 주세요",
            title = if (active) "${selected.displayName} 운동 중" else null,
            countUnit = if (active) selected.displayName else null
        )
    }

    /** Legacy automatic-session projection retained for the isolated classifier regression tests. */
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
