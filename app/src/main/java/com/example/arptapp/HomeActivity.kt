package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.databinding.ActivityHomeBinding
import com.example.arptapp.data.preferences.UserSettingsRepository
import com.example.arptapp.data.preferences.LoginPreferences
import com.example.arptapp.data.remote.SupabaseAuthRepository
import com.example.arptapp.data.remote.UserProfileRepository
import com.example.arptapp.data.remote.UserPreferencesRepository
import com.example.arptapp.data.repository.UserSettingsSyncRepository
import com.example.arptapp.core.coroutines.suspendResult
import com.example.arptapp.utils.AlarmHelper
import com.example.arptapp.ui.gym.NearbyGymController
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

/** 화면 이동·버튼 바인딩을 담당합니다. 설정 병합/위치 획득은 각 전용 모듈에 위임합니다. */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var nearbyGymController: NearbyGymController
    private val settingsRepository by lazy { UserSettingsRepository(this) }
    private val authRepository = SupabaseAuthRepository()
    private val settingsSync by lazy {
        UserSettingsSyncRepository(settingsRepository, UserProfileRepository(), UserPreferencesRepository())
    }
    private var isLaunchingTraining = false
    private var currentUserId: String? = null
    private var settingsSyncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        nearbyGymController = NearbyGymController(
            this,
            binding.btnFindNearbyGym,
            binding.progressNearbyGym,
            binding.tvNearbyGymStatus
        )

        currentUserId = com.example.arptapp.data.remote.AuthSessionStore.current?.userId
            ?: intent.getStringExtra("USER_ID")
        currentUserId?.let { userId ->
            settingsSyncJob = lifecycleScope.launch {
                suspendResult { settingsSync.synchronize(userId) }
                    .onSuccess {
                        if (com.example.arptapp.data.remote.AuthSessionStore.current?.userId == userId) {
                            AlarmHelper.syncDailyReminder(this@HomeActivity)
                        }
                    }
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
        binding.btnLogout.setOnClickListener {
            binding.btnLogout.isEnabled = false
            // 늦게 끝난 조회가 로그아웃 뒤 이전 계정 설정/알람을 복구하지 않도록 먼저 취소한다.
            settingsSyncJob?.cancel()
            lifecycleScope.launch {
                suspendResult {
                    settingsSyncJob?.join()
                    authRepository.signOut()
                }
                    .onSuccess {
                        LoginPreferences(this@HomeActivity).apply {
                            rememberLogin = false
                            pendingGoogleRememberLogin = null
                        }
                        settingsRepository.clearActiveUser()
                        AlarmHelper.cancelDailyReminder(this@HomeActivity)
                        startActivity(Intent(this@HomeActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        })
                    }
                    .onFailure {
                        binding.btnLogout.isEnabled = true
                        Toast.makeText(this@HomeActivity, "로그아웃에 실패했습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                    }
            }
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
