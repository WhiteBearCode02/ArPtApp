package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.data.remote.AuthSessionStore
import com.example.arptapp.databinding.ActivityHistoryBinding
import com.example.arptapp.presentation.report.ReportNavigation
import com.example.arptapp.presentation.report.WorkoutReportData
import kotlinx.coroutines.launch

/** 현재 계정의 기록을 최신순으로 표시하고 공용 보고서 계약으로 상세 화면을 연다. */
class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.btnOpenStatistics.setOnClickListener {
            startActivity(Intent(this, WorkoutStatisticsActivity::class.java))
        }
        loadExerciseHistory()
    }

    private fun loadExerciseHistory() {
        val ownerId = AuthSessionStore.current?.userId.orEmpty()
        lifecycleScope.launch {
            val history = if (ownerId.isBlank()) emptyList() else
                AppDatabase.getDatabase(applicationContext).exerciseDao().getAllRecords(ownerId)
            // 조회 중 계정이 변경된 경우 이전 계정의 기록을 화면에 적용하지 않는다.
            if (ownerId != AuthSessionStore.current?.userId.orEmpty()) return@launch
            binding.tvEmptyMessage.visibility = if (history.isEmpty()) View.VISIBLE else View.GONE
            binding.rvHistory.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
            if (history.isNotEmpty()) {
                binding.rvHistory.adapter = ExerciseAdapter(history, ::openDetailedReport)
            }
        }
    }

    private fun openDetailedReport(record: ExerciseRecord) {
        if (record.userId != AuthSessionStore.current?.userId || record.userId.isBlank()) return
        startActivity(ReportNavigation.intent(this, WorkoutReportData.fromRecord(record)))
    }
}
