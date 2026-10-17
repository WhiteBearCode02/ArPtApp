package com.example.arptapp.ui.dashboard

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.example.arptapp.BuildConfig
import com.example.arptapp.databinding.ViewYoloDiagnosticsPanelBinding

/**
 * In-flow, bounded bottom panel, NOT a floating window/overlay. Expansion consumes allocated body
 * space above the count/banner; the fixed header and CTA live outside that scroll viewport.
 * These scalar diagnostics never change a detector, session or persisted report.
 */
class YoloDiagnosticsPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val binding = ViewYoloDiagnosticsPanelBinding.inflate(LayoutInflater.from(context), this, true)
    init { visibility = View.GONE }

    fun setExpanded(expanded: Boolean) {
        visibility = if (BuildConfig.DEBUG && expanded) View.VISIBLE else View.GONE
    }

    fun updateDiagnostics(text: String) {
        if (!BuildConfig.DEBUG) return
        binding.tvYoloDiagnostics.text = text
    }

    fun updatePerformance(text: String) {
        if (!BuildConfig.DEBUG) return
        binding.tvPerformance.text = text
    }

    /** Debug variant content remains inside the existing bounded scroll viewport, never an overlay. */
    fun attachValidationControls(view: View) {
        if (BuildConfig.DEBUG) (binding.root.getChildAt(0) as? ViewGroup)?.addView(view)
    }
}
