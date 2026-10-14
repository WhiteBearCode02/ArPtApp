package com.example.arptapp.presentation.report

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.data.remote.AiCoachingPolicy
import com.example.arptapp.domain.report.AiReportState
import com.example.arptapp.domain.report.StructuredRepAnalysis
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
    private val aiViewModel: AiReportViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 결과/과거 기록 모두 같은 계약으로 읽는다. 손상된 구버전 회차 JSON은 빈 상태로 표시한다.
        val report = ReportNavigation.read(intent)
        val analyses = ReportNavigation.analyses(report)
        val structured = StructuredReportCodec.decode(report.analysesJson)
        
        // UI 업데이트
        setupUI(report, analyses)
        
        // 차트 그리기
        if (analyses.isNotEmpty()) {
            binding.chart.visibility = View.VISIBLE
            binding.tvChartEmpty.visibility = View.GONE
            setupChart(analyses)
            setupRepDetails(analyses, structured?.repAnalyses.orEmpty())
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
        binding.btnGenerateAi.isEnabled = AiCoachingPolicy.ENABLED && structured?.repAnalyses?.isNotEmpty() == true
        if (AiCoachingPolicy.ENABLED) binding.tvAiStatus.text = if (structured == null) {
            "이전 기록에는 AI 설명에 필요한 구조화된 회차 측정이 없습니다. 로컬 분석을 확인해 주세요."
        } else "버튼을 누르면 관절 각도·관찰 요약만 서버로 전송됩니다. 영상·좌표·계정 정보는 LLM에 전달하지 않습니다."
        binding.btnGenerateAi.setOnClickListener {
            if (structured != null) androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("AI 설명 생성")
                .setMessage("회차별 관절 각도와 관찰 요약을 서버 및 승인된 LLM 제공자에 전달합니다. 영상·관절 좌표·이메일은 전달하지 않습니다. AI 설명은 참고용이며 로컬 분석을 대체하지 않습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("생성") { _, _ -> aiViewModel.generate(structured) }.show()
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                aiViewModel.state.collect { state ->
                    binding.aiProgress.visibility = if (state is AiReportState.Loading) View.VISIBLE else View.GONE
                    binding.btnGenerateAi.isEnabled = AiCoachingPolicy.ENABLED && structured != null &&
                        state !is AiReportState.Loading && state !is AiReportState.Success
                    when (state) {
                        AiReportState.Idle -> Unit
                        AiReportState.Loading -> binding.tvAiStatus.text = "AI 설명을 생성하고 있습니다. 로컬 분석은 계속 확인할 수 있습니다."
                        is AiReportState.Unavailable -> binding.tvAiStatus.text = state.message
                        is AiReportState.Error -> binding.tvAiStatus.text = state.message
                        is AiReportState.Success -> {
                            binding.tvAiStatus.text = "AI가 로컬 측정을 풀어 설명했습니다. 수치와 관찰 내용은 위의 로컬 분석을 우선해 주세요."
                            binding.tvAiReport.visibility = View.VISIBLE
                            binding.tvAiReport.text = with(state.report) {
                                "$summary\n\n관찰된 강점\n${strengths.joinToString("\n")}\n\n확인할 점\n${improvements.joinToString("\n")}\n\n우선 코칭\n$priority\n\n다음 운동 목표\n$nextWorkoutGoal\n\n" +
                                    repFeedback.joinToString("\n\n") { "${it.repNumber}회\n${it.summary}\n${it.positive}\n${it.improvement}" }
                            }
                        }
                    }
                }
            }
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

    private fun setupRepDetails(analyses: List<RepAnalysis>, structured: List<StructuredRepAnalysis>) {
        binding.repAnalysisContainer.removeAllViews()
        analyses.forEach { analysis ->
            val item = TextView(this).apply {
                val measured = structured.firstOrNull { it.repNumber == analysis.repNumber }
                text = "${analysis.repNumber}회 · ${analysis.score.toInt()}점\n" +
                    (measured?.let(StructuredReportPresentation::detail) ?: analysis.detail)
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
