package com.example.arptapp.ui.dashboard

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import com.example.arptapp.R
import com.example.arptapp.databinding.DialogPersonalReferenceBinding
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType

/** Selection UI only: capture, validation and storage stay in the existing Activity/repository. */
object PersonalReferenceDialog {
    fun show(context: Context, cameraView: CameraView, onSelected: (ExerciseType) -> Unit): AlertDialog? {
        if (cameraView == CameraView.UNKNOWN) return null
        val binding = DialogPersonalReferenceBinding.inflate(LayoutInflater.from(context))
        ViewCompat.setAccessibilityHeading(binding.tvReferenceTitle, true)
        ViewCompat.setScreenReaderFocusable(binding.btnReferenceSquat, true)
        ViewCompat.setScreenReaderFocusable(binding.btnReferenceShoulderPress, true)
        val direction = context.getString(if (cameraView == CameraView.FRONT)
            R.string.personal_reference_front else R.string.personal_reference_side)
        binding.tvReferenceCameraView.text = context.getString(R.string.personal_reference_camera_view_value, direction)
        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        // A rapid double tap cannot start a second capture after this dialog has been dismissed.
        var selected = false
        fun select(type: ExerciseType) {
            if (selected || !dialog.isShowing) return
            selected = true
            dialog.dismiss()
            onSelected(type)
        }
        binding.btnReferenceSquat.setOnClickListener { select(DashboardUiPolicy.referenceExerciseOptions[0]) }
        binding.btnReferenceShoulderPress.setOnClickListener { select(DashboardUiPolicy.referenceExerciseOptions[1]) }
        binding.btnCancelReference.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) }
        dialog.show()
        return dialog
    }
}
