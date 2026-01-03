package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityHomeBinding

/**
 * [ArPtApp - Home Hub Module]
 * 역할: 로그인된 사용자에게 개인화된 정보를 보여주고 서비스 진입점을 관리합니다.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. [Data Reception] 인텐트로 전달된 사용자 이름 수신
        val userName = intent.getStringExtra("USER_NAME") ?: "회원"
        binding.tvWelcomeName.text = "${userName}님, 반갑습니다!"

        // 2. [Navigation: AI PT] 카드 클릭 시 대시보드(카메라 화면)로 이동
        binding.cardStartExercise.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }

        // 3. [Navigation: History] 카드 클릭 시 기록 확인(DB 리스트) 화면으로 이동
        binding.cardViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }
}