package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.databinding.ActivityHomeBinding
import com.example.arptapp.data.preferences.UserSettingsRepository
import com.example.arptapp.utils.AlarmHelper
import com.example.arptapp.viewmodel.HomeViewModel
import com.example.arptapp.viewmodel.WorkoutTrendsUiState
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private val homeViewModel: HomeViewModel by viewModels()
    private val settingsRepository by lazy { UserSettingsRepository(this) }
    private var isLaunchingTraining = false
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUserId = intent.getStringExtra("USER_ID")
            ?: com.example.arptapp.data.remote.AuthSessionStore.current?.userId
        currentUserId?.let { userId ->
            lifecycleScope.launch {
                settingsRepository.activateUser(userId)
                AlarmHelper.syncDailyReminder(this@HomeActivity)
            }
        }

        val isAdmin = intent.getBooleanExtra("IS_ADMIN", false) ||
            com.example.arptapp.data.remote.AuthSessionStore.current?.isAdmin == true
        binding.cardAdminDashboard.visibility = if (isAdmin) View.VISIBLE else View.GONE
        binding.cardAdminDashboard.setOnClickListener {
            startActivity(Intent(this, AdminDashboardActivity::class.java))
        }

        val userName = intent.getStringExtra("USER_NAME") ?: "회원"
        binding.tvWelcomeName.text = "${userName}님, 반갑습니다"

        observeWorkoutTrends()
        binding.btnRefreshTrends.setOnClickListener { homeViewModel.loadTrends() }

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

        binding.cardPersonalSettings.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val userId = currentUserId ?: return
        lifecycleScope.launch {
            val nickname = settingsRepository.getSettings(userId).nickname
            if (nickname.isNotBlank()) binding.tvWelcomeName.text = "${nickname}님, 반갑습니다"
        }
    }

    private fun observeWorkoutTrends() {
        lifecycleScope.launch {
            homeViewModel.trends.collect { state ->
                when (state) {
                    WorkoutTrendsUiState.Idle -> {
                        binding.btnRefreshTrends.isEnabled = true
                        binding.tvTrendStatus.text = "조회 버튼을 누르면 무료 조회 한도에서 1회 사용합니다."
                        showTrendMessage("운동 영상 순위를 조회해 주세요")
                    }
                    WorkoutTrendsUiState.Loading -> {
                        binding.btnRefreshTrends.isEnabled = false
                        binding.tvTrendStatus.text = "YouTube 공개 영상 데이터를 불러오는 중입니다."
                        showTrendMessage("공개 영상 데이터 불러오는 중")
                    }

                    is WorkoutTrendsUiState.Content -> {
                        binding.btnRefreshTrends.isEnabled = true
                        binding.tvTrendStatus.text =
                            "공개 영상 표본의 누적 조회수 · 오늘 남은 무료 조회 ${state.snapshot.remainingRequests}회"
                        setTrendRows(state.snapshot.trends.map { trend ->
                            "${localizeExerciseName(trend.exercise)}     상대 조회수 ${trend.score}"
                        })
                    }

                    is WorkoutTrendsUiState.Unavailable -> {
                        binding.btnRefreshTrends.isEnabled = true
                        binding.tvTrendStatus.text = state.message
                        showTrendMessage(state.message)
                    }
                }
            }
        }
    }

    private fun setTrendRows(rows: List<String>) {
        binding.tvTrendSecond.visibility = View.VISIBLE
        binding.tvTrendThird.visibility = View.VISIBLE
        val displayRows = (rows + List(3) { "표시할 데이터 없음" }).take(3)
        binding.tvTrendFirst.text = "01  ${displayRows[0]}"
        binding.tvTrendSecond.text = "02  ${displayRows[1]}"
        binding.tvTrendThird.text = "03  ${displayRows[2]}"
    }

    private fun showTrendMessage(message: String) {
        binding.tvTrendFirst.text = message
        binding.tvTrendSecond.visibility = View.GONE
        binding.tvTrendThird.visibility = View.GONE
    }

    private fun localizeExerciseName(name: String): String = when (name.lowercase()) {
        "squat" -> "스쿼트"
        "full body workout" -> "전신 운동"
        "shoulder press" -> "숄더 프레스"
        else -> name
    }
}
