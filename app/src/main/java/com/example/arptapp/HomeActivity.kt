package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.databinding.ActivityHomeBinding
import com.example.arptapp.utils.AlarmHelper
import com.example.arptapp.viewmodel.HomeViewModel
import com.example.arptapp.viewmodel.WorkoutTrendsUiState
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val homeViewModel: HomeViewModel by viewModels()
    private var isLaunchingTraining = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AlarmHelper.setupDailyReminder(this)

        val userName = intent.getStringExtra("USER_NAME") ?: "회원"
        binding.tvWelcomeName.text = "${userName}님, 반갑습니다"

        observeWorkoutTrends()
        binding.btnRefreshTrends.setOnClickListener { homeViewModel.loadTrends() }
        homeViewModel.loadTrends()

        binding.cardStartExercise.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> binding.starBurstView.startOrbit()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> binding.starBurstView.stopOrbit()
            }
            false
        }

        binding.cardStartExercise.setOnClickListener {
            if (isLaunchingTraining) return@setOnClickListener
            isLaunchingTraining = true
            binding.cardStartExercise.isEnabled = false
            binding.starBurstView.burst {
                startActivity(Intent(this, DashboardActivity::class.java))
                isLaunchingTraining = false
                binding.cardStartExercise.isEnabled = true
            }
        }

        binding.cardViewHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
    }

    private fun observeWorkoutTrends() {
        lifecycleScope.launch {
            homeViewModel.trends.collect { state ->
                when (state) {
                    WorkoutTrendsUiState.Loading -> {
                        binding.btnRefreshTrends.isEnabled = false
                        binding.tvTrendStatus.text = "YouTube 공개 영상 데이터를 불러오는 중입니다."
                        setTrendRows(listOf("공개 영상 데이터 불러오는 중", "대시보드 준비 중", "잠시만 기다려 주세요"))
                    }

                    is WorkoutTrendsUiState.Content -> {
                        binding.btnRefreshTrends.isEnabled = true
                        binding.tvTrendStatus.text = if (state.snapshot.isCached) {
                            "저장된 공개 영상 조회수 데이터 · 6시간마다 갱신"
                        } else {
                            "공개 영상 표본의 누적 조회수 · 방금 갱신"
                        }
                        setTrendRows(state.snapshot.trends.map { trend ->
                            "${localizeExerciseName(trend.exercise)}     상대 조회수 ${trend.score}"
                        })
                    }

                    is WorkoutTrendsUiState.Unavailable -> {
                        binding.btnRefreshTrends.isEnabled = true
                        binding.tvTrendStatus.text = "공개 영상 데이터 서비스를 준비하고 있습니다."
                        setTrendRows(listOf("서비스 준비 중", "개인 검색 기록은 사용하지 않음", "설정 후 새로고침해 주세요"))
                    }
                }
            }
        }
    }

    private fun setTrendRows(rows: List<String>) {
        val displayRows = (rows + List(3) { "표시할 데이터 없음" }).take(3)
        binding.tvTrendFirst.text = "01  ${displayRows[0]}"
        binding.tvTrendSecond.text = "02  ${displayRows[1]}"
        binding.tvTrendThird.text = "03  ${displayRows[2]}"
    }

    private fun localizeExerciseName(name: String): String = when (name.lowercase()) {
        "squat" -> "스쿼트"
        "full body workout" -> "전신 운동"
        "shoulder press" -> "숄더 프레스"
        else -> name
    }
}
