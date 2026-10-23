package com.example.arptapp.presentation.diagnostics

import android.view.View
import androidx.core.view.ViewCompat
import com.example.arptapp.R
import com.example.arptapp.databinding.ViewYoloDiagnosticsHelpBinding

/** Shared, screen-local explanation disclosure. No preference, permission, model or network writes. */
object YoloDiagnosticsHelp {
    fun bind(binding: ViewYoloDiagnosticsHelpBinding) {
        var expanded = false
        fun display() {
            binding.tvYoloGuide.visibility = if (expanded) View.VISIBLE else View.GONE
            binding.btnYoloGuide.setText(if (expanded) R.string.yolo_guide_hide else R.string.yolo_guide_show)
            ViewCompat.setStateDescription(binding.btnYoloGuide, binding.root.context.getString(
                if (expanded) R.string.report_expanded else R.string.report_collapsed))
        }
        display()
        binding.btnYoloGuide.setOnClickListener { expanded = !expanded; display() }
    }
}
