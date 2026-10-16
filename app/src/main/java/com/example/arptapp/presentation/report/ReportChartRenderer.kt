package com.example.arptapp.presentation.report

import androidx.core.content.ContextCompat
import com.example.arptapp.R
import com.example.arptapp.model.RepAnalysis
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter

/** Displays already-stored scores. Color conveys series identity, never safe/normal/danger grades. */
object ReportChartRenderer {
    fun render(chart: BarChart, analyses: List<RepAnalysis>) {
        val context = chart.context
        val primary = ContextCompat.getColor(context, R.color.text_primary)
        val secondary = ContextCompat.getColor(context, R.color.text_secondary)
        val dataSet = BarDataSet(analyses.map { BarEntry(it.repNumber.toFloat(), it.score) }, "회차별 점수").apply {
            color = ContextCompat.getColor(context, R.color.aurora_mint)
            valueTextSize = 12f
            valueTextColor = primary
        }
        val repNumbers = analyses.map { it.repNumber }.toSet()
        chart.apply {
            data = BarData(dataSet).apply { barWidth = 0.65f }
            description.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = secondary
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        if (value.toInt().toFloat() == value && value.toInt() in repNumbers) "${value.toInt()}회" else ""
                }
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                textColor = secondary
                gridColor = ContextCompat.getColor(context, R.color.color_border)
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            setFitBars(true)
            setVisibleXRangeMaximum(8f)
            // Leave vertical gestures to the report; horizontal chart panning remains available.
            setScaleEnabled(false)
            isHighlightPerTapEnabled = false
            contentDescription = context.getString(R.string.report_chart_accessibility)
            invalidate()
        }
    }
}
