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

    /**
     * Project state into mutually exclusive setup and workout-summary sections.
     *
     * A reference recording keeps its SAVE action in the setup card. If an inconsistent pair of
     * active flags arrives during a transition, the active workout wins so setup never overlays it.
     * This is presentation only; it does not start/stop a workout or a reference recording.
     */
    fun displayState(
        isExercising: Boolean,
        isRecordingReference: Boolean
    ): DashboardDisplayState = when {
        isExercising -> DashboardDisplayState(showSetup = false, showWorkoutSummary = true)
        isRecordingReference -> DashboardDisplayState(showSetup = true, showWorkoutSummary = false)
        else -> DashboardDisplayState(showSetup = true, showWorkoutSummary = false)
    }

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

data class DashboardDisplayState(
    val showSetup: Boolean,
    val showWorkoutSummary: Boolean
)

data class DashboardButtonState(
    val cameraViewEnabled: Boolean,
    val personalReferenceEnabled: Boolean,
    val startExerciseEnabled: Boolean
)
