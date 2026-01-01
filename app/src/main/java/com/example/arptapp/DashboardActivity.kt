package com.example.arptapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityDashboardBinding


 // ArPtApp -> Module: Dashboard
 // 운동 시작 및 개인 맞춤형 데이터 요약 화면 제어
 // ViewBinding을 통한 안전한 UI 참조 및 인터랙션 처리

class DashboardActivity : AppCompatActivity() {

    // 메모리 효율과 안전한 참조를 위한 ViewBinding 선언
    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 바인딩 초기화: XML 레이아웃을 코틀린 객체로 변환
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Start Exercise
        // 사용자가 'AI 운동 분석 시작하기' 버튼을 눌렀을 때의 동작 정의
        binding.btnStartExercise.setOnClickListener {
            // TODO: 카메라 권한 확인 및 AI 분석 화면(CameraActivity)으로 전환 로직 필요
            Toast.makeText(this, "AI 카메라를 준비하고 있습니다...", Toast.LENGTH_SHORT).show()
        }
    }
}