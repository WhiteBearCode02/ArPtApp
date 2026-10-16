package com.example.arptapp.presentation.report

import android.view.LayoutInflater
import android.view.View
import androidx.core.view.ViewCompat
import com.example.arptapp.BuildConfig
import com.example.arptapp.R
import com.example.arptapp.databinding.ActivityReportBinding
import com.example.arptapp.databinding.ItemReportMetricGroupBinding
import com.example.arptapp.databinding.ItemReportMetricRowBinding

/** DEBUG-only read-only local diagnostics, deliberately separate from every coaching/score card. */
class YoloSessionDiagnosticsRenderer(private val inflater: LayoutInflater) {
    fun render(binding: ActivityReportBinding, json: String?) {
        binding.yoloShadowSection.visibility = View.GONE
        binding.yoloShadowDetails.visibility = View.GONE
        binding.yoloShadowMetricGroups.removeAllViews()
        binding.btnToggleYoloShadow.setOnClickListener(null)
        val diagnostics = YoloSessionDiagnosticsPresentation.visibleDiagnostics(BuildConfig.DEBUG, json) ?: return
        binding.yoloShadowSection.visibility = View.VISIBLE
        val state = YoloDiagnosticsExpansionState()
        var bound = false
        fun display() {
            if (state.expanded && !bound) {
                YoloSessionDiagnosticsPresentation.groups(diagnostics).forEach { model ->
                    val group = ItemReportMetricGroupBinding.inflate(inflater, binding.yoloShadowMetricGroups, false)
                    group.tvMetricGroupTitle.text = model.title
                    ViewCompat.setAccessibilityHeading(group.tvMetricGroupTitle, true)
                    model.rows.forEach { modelRow ->
                        val row = ItemReportMetricRowBinding.inflate(inflater, group.metricRows, false)
                        row.tvMetricLabel.text = modelRow.label
                        row.tvMetricValue.text = modelRow.value
                        ViewCompat.setScreenReaderFocusable(row.root, true)
                        group.metricRows.addView(row.root)
                    }
                    binding.yoloShadowMetricGroups.addView(group.root)
                }
                bound = true
            }
            binding.yoloShadowDetails.visibility = if (state.expanded) View.VISIBLE else View.GONE
            binding.btnToggleYoloShadow.setText(if (state.expanded) R.string.yolo_report_hide else R.string.yolo_report_show)
            ViewCompat.setStateDescription(binding.btnToggleYoloShadow, binding.root.context.getString(
                if (state.expanded) R.string.report_expanded else R.string.report_collapsed))
        }
        display()
        binding.btnToggleYoloShadow.setOnClickListener { state.toggle(); display() }
    }
}
