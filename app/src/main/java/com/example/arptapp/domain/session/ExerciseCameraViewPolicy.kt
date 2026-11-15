package com.example.arptapp.domain.session

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType

/**
 * Start-screen admission, not new biomechanical thresholds or automatic view detection.
 *
 * Mirrors the existing ExerciseMeasurements/PoseAngleExtractor support and the camera-view table
 * in docs/EXERCISE_EXPANSION_7.md: SIDE_BEND has only a FRONT measurement; PUSH_UP/ROW can measure
 * elbows in FRONT, but SIDE additionally exposes body-line/torso observations and is recommended.
 * A recommendation must not silently turn that existing FRONT support into a prohibition.
 */
object ExerciseCameraViewPolicy {
    fun assess(type: ExerciseType, view: CameraView): ExerciseCameraViewAdmission = when {
        type !in ExerciseType.supported -> ExerciseCameraViewAdmission.UNSUPPORTED
        view == CameraView.UNKNOWN -> ExerciseCameraViewAdmission.VIEW_REQUIRED
        type == ExerciseType.SIDE_BEND && view != CameraView.FRONT ->
            ExerciseCameraViewAdmission.FRONT_REQUIRED
        type in setOf(ExerciseType.PUSH_UP, ExerciseType.BENT_OVER_ROW) && view == CameraView.FRONT ->
            ExerciseCameraViewAdmission.SIDE_RECOMMENDED
        else -> ExerciseCameraViewAdmission.ALLOWED
    }

    /** Re-check when accepting a selection; the activity must also verify its own active state. */
    fun canStart(type: ExerciseType, view: CameraView): Boolean = when (assess(type, view)) {
        ExerciseCameraViewAdmission.ALLOWED, ExerciseCameraViewAdmission.SIDE_RECOMMENDED -> true
        else -> false
    }
}

enum class ExerciseCameraViewAdmission {
    ALLOWED,
    SIDE_RECOMMENDED,
    FRONT_REQUIRED,
    VIEW_REQUIRED,
    UNSUPPORTED
}
