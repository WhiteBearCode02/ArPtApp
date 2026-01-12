package com.example.arptapp.presentation.report

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityReportBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter

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
        val scores = intent.getFloatArrayExtra("SCORES") ?: floatArrayOf()
        
        // UI 업데이트
        setupUI(exerciseType, totalCount, avgScore, scores)
        
        // 차트 그리기
        if (scores.isNotEmpty()) {
            setupChart(scores)
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
        scores: FloatArray
    ) {
        binding.tvExerciseType.text = exerciseType
        binding.tvTotalCount.text = "총 $totalCount 회"
        binding.tvAvgScore.text = "평균 정확도: ${avgScore.toInt()}%"
        
        // 평균 점수에 따른 색상 변경
        binding.tvAvgScore.setTextColor(
            when {
                avgScore >= 90 -> Color.parseColor("#4CAF50")
                avgScore >= 70 -> Color.parseColor("#FF9800")
                else -> Color.parseColor("#F44336")
            }
        )
        
        // 피드백 메시지
        val feedback = when {
            avgScore >= 90 -> "완벽합니다! 훌륭한 자세를 유지했습니다."
            avgScore >= 70 -> "좋습니다! 조금만 더 신경쓰면 완벽해질 거예요."
            else -> "자세를 더 신경써주세요. 천천히 정확하게 해보세요."
        }
        binding.tvFeedback.text = feedback
    }
    
    private fun setupChart(scores: FloatArray) {
        val entries = scores.mapIndexed { index, score ->
            BarEntry((index + 1).toFloat(), score)
        }
        
        val dataSet = BarDataSet(entries, "세트별 정확도").apply {
            // 점수에 따른 색상 설정
            colors = scores.map { score ->
                when {
                    score >= 90 -> Color.parseColor("#4CAF50")
                    score >= 70 -> Color.parseColor("#FF9800")
                    else -> Color.parseColor("#F44336")
                }
            }
            valueTextSize = 12f
            valueTextColor = Color.WHITE
        }
        
        val barData = BarData(dataSet)
        
        binding.chart.apply {
            data = barData
            description.isEnabled = false
            
            // X축 설정
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                textColor = Color.WHITE
                valueFormatter = IndexAxisValueFormatter(
                    (1..scores.size).map { "${it}회" }
                )
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
            invalidate()
        }
    }
}
