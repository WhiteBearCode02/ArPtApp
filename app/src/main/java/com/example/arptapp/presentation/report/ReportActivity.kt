package com.example.arptapp.presentation.report

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityReportBinding
import com.example.arptapp.model.RepAnalysis
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.util.Locale

class ReportActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityReportBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 결과/과거 기록 모두 같은 계약으로 읽는다. 손상된 구버전 회차 JSON은 빈 상태로 표시한다.
        val report = ReportNavigation.read(intent)
        val analyses = ReportNavigation.analyses(report)
        
        // UI 업데이트
        setupUI(report, analyses)
        
        // 차트 그리기
        if (analyses.isNotEmpty()) {
            binding.chart.visibility = View.VISIBLE
            binding.tvChartEmpty.visibility = View.GONE
            setupChart(analyses)
            setupRepDetails(analyses)
        } else {
            binding.chart.visibility = View.GONE
            binding.tvChartEmpty.visibility = View.VISIBLE
            binding.tvRepDetailsHeader.visibility = View.GONE
            binding.tvRepAnalysisNote.visibility = View.GONE
            binding.repAnalysisContainer.visibility = View.GONE
        }
        
        // 닫기 버튼
        binding.btnClose.setOnClickListener {
            finish()
        }
    }
    
    private fun setupUI(report: WorkoutReportData, analyses: List<RepAnalysis>) {
        binding.tvExerciseType.text = report.exerciseType
        binding.tvTotalCount.text = "총 ${report.totalCount} 회"
        binding.tvWorkoutDate.text = report.workoutDate.ifBlank { "운동 일시 정보 없음" }
        binding.tvDuration.text = "운동 시간: ${WorkoutReportPresentation.formatDuration(report.durationSeconds)}"
        binding.tvCalories.text = String.format(
            Locale.getDefault(),
            "예상 소모 칼로리: %.1f kcal",
            report.burnedCalories ?: 0.0
        )

        if (analyses.isEmpty()) {
            binding.tvAvgScore.text = "자세 점수: 기록 없음"
            binding.tvAvgScore.setTextColor(Color.parseColor("#B0B0B0"))
            binding.tvFeedback.text = report.feedbackMessage.ifBlank {
                "이전 버전에서 저장된 기록으로, 회차별 자세 분석 데이터가 없습니다."
            }
            return
        }

        val displayScore = WorkoutReportPresentation.displayScore(report, analyses) ?: return
        binding.tvAvgScore.text = "평균 자세 점수: ${displayScore.toInt()}점"
        
        // 평균 점수에 따른 색상 변경
        binding.tvAvgScore.setTextColor(
            when {
                displayScore >= 90 -> Color.parseColor("#4CAF50")
                displayScore >= 70 -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            }
        )
        
        // 저장된 관찰 내용을 우선한다. 요약이 없을 때도 점수를 임상적 자세 판정으로 표현하지 않는다.
        binding.tvFeedback.text = report.feedbackMessage.ifBlank {
            WorkoutReportPresentation.feedback(displayScore)
        }
    }
    
    private fun setupChart(analyses: List<RepAnalysis>) {
        val entries = analyses.mapIndexed { index, analysis ->
            BarEntry((index + 1).toFloat(), analysis.score)
        }
        
        val dataSet = BarDataSet(entries, "횟수별 자세 점수").apply {
            // 점수에 따른 색상 설정
            colors = analyses.map { analysis ->
                val score = analysis.score
                when {
                    score >= 90 -> Color.parseColor("#4CAF50")
                    score >= 70 -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#F44336")
                }
            }
            valueTextSize = 12f
            valueTextColor = Color.WHITE
        }
        
        val barData = BarData(dataSet).apply { barWidth = 0.65f }
        
        binding.chart.apply {
            data = barData
            description.isEnabled = false
            
            // X축 설정
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = Color.WHITE
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String =
                        if (value.toInt().toFloat() == value && value in 1f..analyses.size.toFloat()) {
                            "${value.toInt()}회"
                        } else ""
                }
            }
            
            // Y축 설정
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                textColor = Color.WHITE
                setDrawGridLines(true)
                gridColor = Color.parseColor("#424242")
            }
            
            axisRight.isEnabled = false
            
            // 범례
            legend.apply {
                textColor = Color.WHITE
                textSize = 14f
            }
            
            // 애니메이션
            animateY(1000)
            
            setFitBars(true)
            setVisibleXRangeMaximum(8f)
            invalidate()
        }
    }

    private fun setupRepDetails(analyses: List<RepAnalysis>) {
        binding.repAnalysisContainer.removeAllViews()
        analyses.forEach { analysis ->
            val item = TextView(this).apply {
                text = "${analysis.repNumber}회 · ${analysis.score.toInt()}점\n${analysis.detail}"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(18.dp, 14.dp, 18.dp, 14.dp)
                setBackgroundResource(com.example.arptapp.R.drawable.bg_trend_row)
            }
            binding.repAnalysisContainer.addView(item, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp })
        }
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
