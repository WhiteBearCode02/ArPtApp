package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.databinding.ActivityHistoryBinding
import com.example.arptapp.presentation.report.ReportActivity
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * 저장된 모든 운동 기록을 최신순으로 보여주는 화면입니다.
 */
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 리사이클러뷰 설정 (수직 리스트 형태)
        binding.rvHistory.layoutManager = LinearLayoutManager(this)

        // 데이터 로딩 실행
        loadExerciseHistory()
    }

    private fun loadExerciseHistory() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            // 최신순으로 정렬된 모든 기록 수신
            val historyList = db.exerciseDao().getAllRecords()

            if (historyList.isEmpty()) {
                // 기록이 없을 경우 안내 문구 노출
                binding.tvEmptyMessage.visibility = View.VISIBLE
                binding.rvHistory.visibility = View.GONE
            } else {
                binding.tvEmptyMessage.visibility = View.GONE
                binding.rvHistory.visibility = View.VISIBLE
                binding.rvHistory.adapter = ExerciseAdapter(historyList, ::openDetailedReport)
            }
        }
    }

    private fun openDetailedReport(record: com.example.arptapp.data.ExerciseRecord) {
        val scores = runCatching {
            val array = JSONArray(record.scoresJson)
            FloatArray(array.length()) { index -> array.optDouble(index, 0.0).toFloat() }
        }.getOrDefault(floatArrayOf())

        startActivity(Intent(this, ReportActivity::class.java).apply {
            putExtra("EXERCISE_TYPE", record.exerciseType.ifBlank { "운동 기록" })
            putExtra("TOTAL_COUNT", record.totalCount)
            putExtra("AVG_SCORE", record.averageScore)
            putExtra("SCORES", scores)
            putExtra("WORKOUT_DATE", record.date)
            putExtra("EXERCISE_TIME", record.duration)
            putExtra("BURNED_CALORIES", record.burnedCalories)
            putExtra("FEEDBACK_MESSAGE", record.feedbackMessage)
        })
    }
}
