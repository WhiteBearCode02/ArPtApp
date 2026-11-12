package com.example.arptapp.ui.dashboard

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import com.example.arptapp.R
import com.example.arptapp.databinding.DialogPersonalReferenceBinding
import com.example.arptapp.databinding.ItemPersonalReferenceOptionBinding
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType

/** Selection UI only: capture, validation and storage stay in the existing Activity/repository. */
object PersonalReferenceDialog {
    fun show(context: Context, cameraView: CameraView, onSelected: (ExerciseType) -> Unit): AlertDialog? {
        if (cameraView == CameraView.UNKNOWN) return null
        val inflater = LayoutInflater.from(context)
        val binding = DialogPersonalReferenceBinding.inflate(inflater)
        ViewCompat.setAccessibilityHeading(binding.tvReferenceTitle, true)
        val direction = context.getString(if (cameraView == CameraView.FRONT)
            R.string.personal_reference_front else R.string.personal_reference_side)
        binding.tvReferenceCameraView.text = context.getString(R.string.personal_reference_camera_view_value, direction)
        val dialog = AlertDialog.Builder(context).setView(binding.root).create()
        // A rapid double tap cannot start a second capture after this dialog has been dismissed.
        var selected = false
        fun select(type: ExerciseType) {
            if (selected || !dialog.isShowing) return
            if (!DashboardUiPolicy.canBeginReference(type, cameraView, false, false)) return
            selected = true
            dialog.dismiss()
            onSelected(type)
        }
        // One reusable row per supported policy entry. Adding an exercise never needs XML surgery,
        // but still requires explicit support in both the admission policy and the label mapping.
        DashboardUiPolicy.referenceExerciseOptions.forEach { type ->
            val row = ItemPersonalReferenceOptionBinding.inflate(inflater, binding.referenceOptions, false)
            row.tvReferenceOptionTitle.text = type.displayName
            row.tvReferenceOptionSubtitle.setText(R.string.personal_reference_current_view_hint)
            ViewCompat.setScreenReaderFocusable(row.root, true)
            row.root.isEnabled = DashboardUiPolicy.canBeginReference(type, cameraView, false, false)
            if (!row.root.isEnabled) row.tvReferenceOptionSubtitle.setText(R.string.reference_front_only)
            row.root.setOnClickListener { select(type) }
            binding.referenceOptions.addView(row.root)
        }
        binding.btnCancelReference.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener { dialog.window?.setBackgroundDrawableResource(android.R.color.transparent) }
        dialog.show()
        return dialog
    }

}
