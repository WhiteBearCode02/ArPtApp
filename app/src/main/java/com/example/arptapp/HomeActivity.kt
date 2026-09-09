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

        val userName = intent.getStringExtra("USER_NAME") ?: "Member"
        binding.tvWelcomeName.text = "$userName, welcome"

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
                        binding.tvTrendStatus.text = "Loading public YouTube video trends..."
                        setTrendRows(listOf("Loading public trends...", "Preparing dashboard...", "Please wait..."))
                    }

                    is WorkoutTrendsUiState.Content -> {
                        binding.btnRefreshTrends.isEnabled = true
                        binding.tvTrendStatus.text = if (state.snapshot.isCached) {
                            "Saved public-video interest · refreshes every 6 hours"
                        } else {
                            "Public-video interest · updated just now"
                        }
                        setTrendRows(state.snapshot.trends.map { trend ->
                            "${trend.exercise}     interest ${trend.score}"
                        })
                    }

                    is WorkoutTrendsUiState.Unavailable -> {
                        binding.btnRefreshTrends.isEnabled = true
                        binding.tvTrendStatus.text = "Trend service setup is required."
                        setTrendRows(listOf("Trend service is preparing", "No personal history is used", "Use refresh after setup"))
                    }
                }
            }
        }
    }

    private fun setTrendRows(rows: List<String>) {
        val displayRows = (rows + List(3) { "No trend data" }).take(3)
        binding.tvTrendFirst.text = "01  ${displayRows[0]}"
        binding.tvTrendSecond.text = "02  ${displayRows[1]}"
        binding.tvTrendThird.text = "03  ${displayRows[2]}"
    }
}
