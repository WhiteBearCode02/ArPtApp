package com.example.arptapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityMainBinding


 // [Project: ArPtApp]
 // 사용자 로그인 인터랙션 및 화면 전환 제어

class MainActivity : AppCompatActivity() {

    // ViewBinding: XML의 컴포넌트를 점(.) 문법으로 안전하게 접근
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 레이아웃 바인딩 초기화
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2. Login Logic
        // XML에서 정의한 ID인 btn_login을 바로 사용합니다.
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()

            if (email.isEmpty()) {
                // 이메일이 비었을 때 UX 가이드라인 제공
                Toast.makeText(this, "이메일을 입력해 주세요!", Toast.LENGTH_SHORT).show()
            } else {
                // 로그인 성공 메시지 출력 및 다음 대시보드로 넘어갈 준비
                Toast.makeText(this, "${email}님, 오늘도 멋지게 운동해봐요!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}