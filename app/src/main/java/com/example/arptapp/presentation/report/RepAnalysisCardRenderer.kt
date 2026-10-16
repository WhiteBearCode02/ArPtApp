package com.example.arptapp.presentation.report

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.example.arptapp.R
import com.example.arptapp.databinding.ItemRepAnalysisBinding
import com.example.arptapp.databinding.ItemReportMetricGroupBinding
import com.example.arptapp.databinding.ItemReportMetricRowBinding

/**
 * Binds presentation values, never interprets angles/errors or recalculates a score.
 * Retains the existing report's scrolling container; detail rows inflate lazily per card.
 */
class RepAnalysisCardRenderer(
    private val inflater: LayoutInflater,
    private val expansion: RepMetricExpansionState = RepMetricExpansionState()
) {
    fun render(container: LinearLayout, models: List<RepReportUiModel>) {
        container.removeAllViews()
        models.forEach { model ->
            val card = ItemRepAnalysisBinding.inflate(inflater, container, false)
            card.tvRepNumber.text = container.context.getString(R.string.report_rep_number, model.repNumber)
            card.tvRepScore.text = model.score?.let {
                container.context.getString(R.string.report_score_value, it.toInt())
            } ?: container.context.getString(R.string.report_score_missing)
            ViewCompat.setAccessibilityHeading(card.tvRepNumber, true)
            card.tvRepStatus.text = model.status
            card.tvRepSummary.text = model.summary
            card.strengthsSection.visibility = visible(model.strengths.isNotEmpty())
            card.tvRepStrengths.text = model.strengths.joinToString("\n\n") { "✓ $it" }
            card.improvementsSection.visibility = visible(model.improvements.isNotEmpty())
            card.tvRepImprovements.text = model.improvements.joinToString("\n\n") { "• $it" }
            card.prioritySection.visibility = visible(!model.priorityTip.isNullOrBlank())
            card.tvRepPriority.text = model.priorityTip
            card.tvRepNotes.showText(model.notes.joinToString("\n\n"))
            card.legacySection.visibility = visible(!model.legacyDetail.isNullOrBlank())
            card.tvLegacyDetail.text = model.legacyDetail
            card.btnToggleMetrics.visibility = visible(model.metricGroups.isNotEmpty())

            var rowsBound = false
            fun displayExpanded(expanded: Boolean) {
                if (expanded && !rowsBound) {
                    bindMetricGroups(card.metricGroupsContainer, model.metricGroups)
                    rowsBound = true
                }
                card.metricGroupsContainer.visibility = visible(expanded)
                card.btnToggleMetrics.setText(if (expanded) R.string.report_metrics_hide else R.string.report_metrics_show)
                card.btnToggleMetrics.contentDescription = container.context.getString(
                    if (expanded) R.string.report_metrics_hide_for_rep else R.string.report_metrics_show_for_rep,
                    model.repNumber
                )
                ViewCompat.setStateDescription(card.btnToggleMetrics, container.context.getString(
                    if (expanded) R.string.report_expanded else R.string.report_collapsed
                ))
            }
            displayExpanded(expansion.isExpanded(model.repNumber))
            card.btnToggleMetrics.setOnClickListener { displayExpanded(expansion.toggle(model.repNumber)) }
            container.addView(card.root)
        }
    }

    private fun bindMetricGroups(container: LinearLayout, groups: List<MetricGroupUiModel>) {
        groups.forEach { model ->
            val group = ItemReportMetricGroupBinding.inflate(inflater, container, false)
            group.tvMetricGroupTitle.text = model.title
            ViewCompat.setAccessibilityHeading(group.tvMetricGroupTitle, true)
            model.rows.forEach { value ->
                val row = ItemReportMetricRowBinding.inflate(inflater, group.metricRows, false)
                row.tvMetricLabel.text = value.label
                row.tvMetricValue.text = value.value
                ViewCompat.setScreenReaderFocusable(row.root, true)
                group.metricRows.addView(row.root)
            }
            container.addView(group.root)
        }
    }

    private fun TextView.showText(value: String) {
        text = value
        visibility = visible(value.isNotBlank())
    }

    private fun visible(show: Boolean): Int = if (show) View.VISIBLE else View.GONE
}
