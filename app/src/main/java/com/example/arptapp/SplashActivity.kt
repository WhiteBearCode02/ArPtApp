package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.preferences.LoginPreferences
import com.example.arptapp.data.remote.SupabaseAuthRepository
import com.example.arptapp.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * [AIRPTCoach - Splash Module]
 * 역할: 초기 리소스 로딩 및 브랜딩 노출 후 메인 진입점으로 이동시킵니다.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            delay(2500)
            val preferences = LoginPreferences(this@SplashActivity)
            val repository = SupabaseAuthRepository()
            val session = repository.restoreSession().getOrNull()

            if (!preferences.rememberLogin) {
                if (session != null) runCatching { repository.signOut() }
                openLogin()
                return@launch
            }

            if (session == null) {
                preferences.rememberLogin = false
                openLogin()
            } else {
                startActivity(Intent(this@SplashActivity,
                    if (session.isAdmin) AdminDashboardActivity::class.java else HomeActivity::class.java).apply {
                    putExtra("USER_ID", session.userId)
                    putExtra("USER_EMAIL", session.email)
                    putExtra("IS_ADMIN", session.isAdmin)
                })
                finish()
            }
        }
    }

    private fun openLogin() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
