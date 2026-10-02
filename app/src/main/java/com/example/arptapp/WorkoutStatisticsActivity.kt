package com.example.arptapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.data.remote.AuthSessionStore
import com.example.arptapp.databinding.ActivityWorkoutStatisticsBinding
import com.example.arptapp.domain.statistics.WorkoutStatistics
import kotlinx.coroutines.launch
import java.util.Locale

class WorkoutStatisticsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWorkoutStatisticsBinding
    private var days = 7

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutStatisticsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarStatistics.setNavigationOnClickListener { finish() }
        binding.btnWeek.setOnClickListener { days = 7; load() }
        binding.btnMonth.setOnClickListener { days = 30; load() }
        load()
    }

    private fun load() {
        val requestedDays = days
        val userId = AuthSessionStore.current?.userId.orEmpty()
        lifecycleScope.launch {
            val records = if (userId.isBlank()) emptyList() else AppDatabase.getDatabase(applicationContext).exerciseDao().getAllRecords(userId)
            if (requestedDays != days || userId != AuthSessionStore.current?.userId.orEmpty()) return@launch
            val summary = WorkoutStatistics.summarize(records, userId, requestedDays)
            binding.tvSummary.text = "최근 ${requestedDays}일\n\n운동 ${summary.sessions}회 · ${summary.activeDays}일 참여\n총 ${summary.reps}회 반복 · ${summary.durationSeconds / 60}분\n평균 분석 점수 ${summary.averageScore?.let { String.format(Locale.KOREA, "%.1f점", it) } ?: "기록 없음"}"
            binding.tvDaily.text = if (summary.sessions == 0) "아직 운동 기록이 없습니다. 첫 운동을 시작해 보세요." else
                summary.dailyReps.joinToString("\n") { (date, count) -> "${date.monthValue}월 ${date.dayOfMonth}일     ${count}회" }
        }
    }
}
