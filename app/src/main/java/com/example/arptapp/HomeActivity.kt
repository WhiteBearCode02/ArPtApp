package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.databinding.ActivityHomeBinding
import com.example.arptapp.data.preferences.UserSettingsRepository
import com.example.arptapp.utils.AlarmHelper
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
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

        binding.btnOpenRanking.setOnClickListener {
            startActivity(Intent(this, YouTubeRankingActivity::class.java))
        }

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

}
