package com.example.arptapp.ui.dashboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import com.example.arptapp.R
import com.example.arptapp.databinding.DialogWorkoutSelectionBinding
import com.example.arptapp.databinding.ItemPersonalReferenceOptionBinding
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.session.ExerciseCameraViewAdmission
import com.example.arptapp.domain.session.ExerciseCameraViewPolicy

/**
 * Selection and camera-view guidance only. No classifier, counter, session or storage writes.
 * A dismiss/back/outside tap never calls onSelected. The activity re-checks current state/view
 * before beginning a session because cameraView here is deliberately a preparation snapshot.
 */
object WorkoutSelectionDialog {
    fun show(context: Context, cameraView: CameraView, onSelected: (ExerciseType) -> Unit): AlertDialog {
        val inflater = LayoutInflater.from(context)
        val binding = DialogWorkoutSelectionBinding.inflate(inflater)
        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        ViewCompat.setAccessibilityHeading(binding.tvWorkoutSelectionTitle, true)
        val direction = context.getString(when (cameraView) {
            CameraView.FRONT -> R.string.personal_reference_front
            CameraView.SIDE -> R.string.personal_reference_side
            CameraView.UNKNOWN -> R.string.workout_selection_view_unselected
        })
        binding.tvWorkoutSelectionCameraView.text =
            context.getString(R.string.workout_selection_current_view, direction)

        var selected = false
        var pendingSelection: ExerciseType? = null
        fun completeSelection(type: ExerciseType) {
            // Suppress a rapid second tap, and never accept stale/unsupported dialog options.
            if (selected || !dialog.isShowing || !ExerciseCameraViewPolicy.canStart(type, cameraView)) return
            selected = true
            dialog.dismiss()
            onSelected(type)
        }
        fun showGuidance(type: ExerciseType) {
            pendingSelection = null
            binding.btnContinueFrontWorkout.visibility = View.GONE
            binding.btnReturnToWorkoutSetup.visibility = View.GONE
            binding.tvWorkoutSelectionGuidance.visibility = View.GONE
            when (ExerciseCameraViewPolicy.assess(type, cameraView)) {
                ExerciseCameraViewAdmission.ALLOWED -> completeSelection(type)
                ExerciseCameraViewAdmission.SIDE_RECOMMENDED -> {
                    pendingSelection = type
                    binding.tvWorkoutSelectionGuidance.text =
                        context.getString(R.string.workout_selection_side_recommended, type.displayName)
                    binding.tvWorkoutSelectionGuidance.visibility = View.VISIBLE
                    binding.btnContinueFrontWorkout.visibility = View.VISIBLE
                    binding.btnReturnToWorkoutSetup.visibility = View.VISIBLE
                }
                ExerciseCameraViewAdmission.FRONT_REQUIRED,
                ExerciseCameraViewAdmission.VIEW_REQUIRED -> {
                    binding.tvWorkoutSelectionGuidance.setText(
                        if (cameraView == CameraView.UNKNOWN) R.string.workout_selection_choose_view_first
                        else R.string.workout_selection_front_required)
                    binding.tvWorkoutSelectionGuidance.visibility = View.VISIBLE
                    binding.btnReturnToWorkoutSetup.visibility = View.VISIBLE
                }
                ExerciseCameraViewAdmission.UNSUPPORTED -> Unit
            }
            if (binding.tvWorkoutSelectionGuidance.visibility == View.VISIBLE) {
                binding.tvWorkoutSelectionGuidance.announceForAccessibility(binding.tvWorkoutSelectionGuidance.text)
                // Guidance and its actions must stay reachable even with a short/large-font window.
                binding.root.post { binding.root.smoothScrollTo(0, binding.workoutSelectionGuidanceSection.top) }
            }
        }

        // The capability registry is the only option source. Do not enumerate all enum entries:
        // historical LUNGE/UNKNOWN/IDLE values remain readable but cannot start a workout.
        ExerciseType.supported.forEach { type ->
            val row = ItemPersonalReferenceOptionBinding.inflate(inflater, binding.workoutSelectionOptions, false)
            row.tvReferenceOptionTitle.text = type.displayName
            row.tvReferenceOptionSubtitle.setText(when (type) {
                ExerciseType.SIDE_BEND -> R.string.workout_selection_front_only_hint
                ExerciseType.PUSH_UP, ExerciseType.BENT_OVER_ROW -> R.string.workout_selection_side_hint
                else -> R.string.workout_selection_either_view_hint
            })
            ViewCompat.setScreenReaderFocusable(row.root, true)
            row.root.setOnClickListener { if (!selected && dialog.isShowing) showGuidance(type) }
            binding.workoutSelectionOptions.addView(row.root)
        }
        binding.btnContinueFrontWorkout.setOnClickListener { pendingSelection?.let(::completeSelection) }
        binding.btnReturnToWorkoutSetup.setOnClickListener { dialog.dismiss() }
        binding.btnCancelWorkoutSelection.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) }
        dialog.show()
        return dialog
    }
}
