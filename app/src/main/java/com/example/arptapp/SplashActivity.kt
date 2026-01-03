package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivitySplashBinding

/**
 * [ArPtApp - Splash Module]
 * 역할: 초기 리소스 로딩 및 브랜딩 노출 후 메인 진입점으로 이동시킵니다.
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2.5초(2500ms) 동안 스플래시 화면을 유지한 후 이동
        Handler(Looper.getMainLooper()).postDelayed({
            // 1. 로그인 화면(MainActivity)으로 이동하는 인텐트 생성
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            
            // 2. 스플래시 화면 종료 (뒤로가기 시 다시 나타나지 않도록 제거)
            finish()
        }, 2500)
    }
}