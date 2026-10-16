package com.example.arptapp.ui.dashboard

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType

/**
 * Presentation-only admission rules for the dashboard controls.
 *
 * The activity remains the owner of exercise and calibration state. Keeping these decisions pure
 * makes a view re-render safe: it neither changes the active session nor starts a recording.
 */
object DashboardUiPolicy {
    /** Display order also defines the only two exercises available for personal-reference entry. */
    val referenceExerciseOptions: List<ExerciseType> =
        listOf(ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS)

    fun buttonState(
        isExercising: Boolean,
        isRecordingReference: Boolean
    ): DashboardButtonState = DashboardButtonState(
        cameraViewEnabled = !isExercising && !isRecordingReference,
        // During calibration this button is the existing SAVE action, not a new recording entry.
        personalReferenceEnabled = !isExercising,
        startExerciseEnabled = !isExercising && !isRecordingReference
    )

    /** Re-check immediately after a dialog selection; UI enabled state alone is not a guard. */
    fun canBeginReference(
        type: ExerciseType,
        cameraView: CameraView,
        isExercising: Boolean,
        isRecordingReference: Boolean
    ): Boolean = type in referenceExerciseOptions &&
        cameraView != CameraView.UNKNOWN &&
        !isExercising &&
        !isRecordingReference
}

data class DashboardButtonState(
    val cameraViewEnabled: Boolean,
    val personalReferenceEnabled: Boolean,
    val startExerciseEnabled: Boolean
)
