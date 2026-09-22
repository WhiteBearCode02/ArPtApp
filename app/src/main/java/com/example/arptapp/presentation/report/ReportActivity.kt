package com.example.arptapp.presentation.report

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.data.RepAnalysisCodec
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
        
        // Intent에서 데이터 받기
        val exerciseType = intent.getStringExtra("EXERCISE_TYPE") ?: "스쿼트"
        val totalCount = intent.getIntExtra("TOTAL_COUNT", 0)
        val avgScore = intent.getFloatExtra("AVG_SCORE", 0f)
        val analyses = RepAnalysisCodec.decode(intent.getStringExtra("REP_ANALYSES_JSON")
            ?: org.json.JSONArray(intent.getFloatArrayExtra("SCORES")?.toList() ?: emptyList<Float>()).toString())
        val workoutDate = intent.getStringExtra("WORKOUT_DATE").orEmpty()
        val duration = intent.getLongExtra("EXERCISE_TIME", 0L)
        val calories = intent.getDoubleExtra("BURNED_CALORIES", 0.0)
        val storedFeedback = intent.getStringExtra("FEEDBACK_MESSAGE").orEmpty()
        
        // UI 업데이트
        setupUI(exerciseType, totalCount, avgScore, analyses, workoutDate, duration, calories, storedFeedback)
        
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
    
    private fun setupUI(
        exerciseType: String,
        totalCount: Int,
        avgScore: Float,
        analyses: List<RepAnalysis>,
        workoutDate: String,
        duration: Long,
        calories: Double,
        storedFeedback: String
    ) {
        binding.tvExerciseType.text = exerciseType
        binding.tvTotalCount.text = "총 $totalCount 회"
        binding.tvWorkoutDate.text = workoutDate.ifBlank { "운동 일시 정보 없음" }
        binding.tvDuration.text = "운동 시간: ${formatDuration(duration)}"
        binding.tvCalories.text = String.format(
            Locale.getDefault(),
            "예상 소모 칼로리: %.1f kcal",
            calories
        )

        if (analyses.isEmpty()) {
            binding.tvAvgScore.text = "자세 점수: 기록 없음"
            binding.tvAvgScore.setTextColor(Color.parseColor("#B0B0B0"))
            binding.tvFeedback.text = storedFeedback.ifBlank {
                "이전 버전에서 저장된 기록으로, 회차별 자세 분석 데이터가 없습니다."
            }
            return
        }

        val displayScore = if (avgScore > 0f) avgScore else analyses.map { it.score }.average().toFloat()
        binding.tvAvgScore.text = "평균 자세 점수: ${displayScore.toInt()}점"
        
        // 평균 점수에 따른 색상 변경
        binding.tvAvgScore.setTextColor(
            when {
                displayScore >= 90 -> Color.parseColor("#4CAF50")
                displayScore >= 70 -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            }
        )
        
        // 피드백 메시지
        val feedback = when {
            avgScore >= 90 -> "완벽합니다! 훌륭한 자세를 유지했습니다."
            avgScore >= 70 -> "좋습니다! 조금만 더 신경쓰면 완벽해질 거예요."
            else -> "자세를 더 신경써주세요. 천천히 정확하게 해보세요."
        }
        binding.tvFeedback.text = storedFeedback.ifBlank { feedback }
    }

    private fun formatDuration(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d분 %02d초", minutes, seconds)
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
