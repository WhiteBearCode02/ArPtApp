package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityHomeBinding
import com.example.arptapp.utils.AlarmHelper // 알림 예약 유틸리티 클래스

/**
 * 앱의 메인 허브로서 사용자 인사말 표시, 서비스 화면 전환, 
 * 그리고 일일 운동 알림 스케줄링을 담당합니다.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. [Notification] 매일 오후 8시 운동 권장 알림 스케줄링 등록
        // 사용자가 홈 화면에 진입할 때마다 알림이 최신 상태로 예약됩니다.
        AlarmHelper.setupDailyReminder(this)

        // 2. [Data Reception] 로그인 시 전달받은 사용자 이름 표시
        val userName = intent.getStringExtra("USER_NAME") ?: "회원"
        binding.tvWelcomeName.text = "${userName}님, 반갑습니다!"

        // 3. [Navigation: AI PT] 'AI PT 시작하기' 카드 클릭 시 카메라 분석 화면으로 이동
        binding.cardStartExercise.setOnClickListener {
            val intent = Intent(this, DashboardActivity::class.java)
            startActivity(intent)
        }

        // 4. [Navigation: History] '나의 기록 확인' 카드 클릭 시 DB 목록 화면으로 이동
        binding.cardViewHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }
    }
}