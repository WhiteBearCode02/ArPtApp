package com.example.arptapp.ui.dashboard

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.example.arptapp.BuildConfig
import com.example.arptapp.R
import com.example.arptapp.databinding.ViewYoloDiagnosticsPanelBinding
import com.example.arptapp.domain.detection.DetectorComparison
import com.example.arptapp.presentation.diagnostics.YoloDiagnosticsHelp
import com.example.arptapp.presentation.diagnostics.YoloLiveDiagnosticsPresentation

/**
 * In-flow, bounded bottom panel, NOT a floating window/overlay. Expansion consumes allocated body
 * space above the count/banner; the fixed header and CTA live outside that scroll viewport.
 * These scalar diagnostics never change a detector, session or persisted report.
 */
class YoloDiagnosticsPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val binding = ViewYoloDiagnosticsPanelBinding.inflate(LayoutInflater.from(context), this, true)
    private var latestComparison: DetectorComparison? = null
    private var lastOverviewAt = 0L
    private var technicalExpanded = false
    init {
        visibility = View.GONE
        YoloDiagnosticsHelp.bind(binding.yoloHelp)
        binding.btnYoloTechnical.setOnClickListener { showTechnical(!technicalExpanded) }
    }

    fun setExpanded(expanded: Boolean) {
        visibility = if (BuildConfig.DEBUG && expanded) View.VISIBLE else View.GONE
        if (visibility == View.VISIBLE) renderOverview(force = true) else {
            showTechnical(false)
            YoloDiagnosticsHelp.bind(binding.yoloHelp)
        }
    }

    /** Cache scalar observations while closed; refresh visible prose at most twice a second. */
    fun updateComparison(data: DetectorComparison) {
        if (!BuildConfig.DEBUG) return
        latestComparison = data
        renderOverview()
    }

    private fun renderOverview(force: Boolean = false) {
        if (!BuildConfig.DEBUG || visibility != View.VISIBLE) return
        val data = latestComparison ?: return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastOverviewAt < 500L) return
        lastOverviewAt = now
        val model = YoloLiveDiagnosticsPresentation.present(data)
        setTextIfChanged(binding.tvYoloStatus, model.status)
        setTextIfChanged(binding.tvYoloRecognition, model.recognition)
        setTextIfChanged(binding.tvYoloSpeed, model.performance)
    }

    private fun showTechnical(expanded: Boolean) {
        technicalExpanded = BuildConfig.DEBUG && expanded
        binding.tvYoloDiagnostics.visibility = if (technicalExpanded) View.VISIBLE else View.GONE
        binding.btnYoloTechnical.setText(if (technicalExpanded) R.string.yolo_technical_hide else R.string.yolo_technical_show)
        ViewCompat.setStateDescription(binding.btnYoloTechnical, context.getString(
            if (technicalExpanded) R.string.report_expanded else R.string.report_collapsed))
    }

    private fun setTextIfChanged(view: TextView, text: String) {
        if (view.text.toString() != text) view.text = text
    }

    fun updateDiagnostics(text: String) {
        if (!BuildConfig.DEBUG) return
        setTextIfChanged(binding.tvYoloDiagnostics, text)
    }

    fun updatePerformance(text: String) {
        if (!BuildConfig.DEBUG) return
        setTextIfChanged(binding.tvPerformance, text)
    }

    /** Debug variant content remains inside the existing bounded scroll viewport, never an overlay. */
    fun attachValidationControls(view: View) {
        if (BuildConfig.DEBUG) (binding.root.getChildAt(0) as? ViewGroup)?.addView(view)
    }
}
