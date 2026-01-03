package com.example.arptapp

// [Imports: 안드로이드 프레임워크 및 화면 전환 도구]
import android.content.Intent // 액티비티 간 메시지 및 화면 전환 담당
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityMainBinding // 뷰 바인딩

/**
 * [ArPtApp - Authentication Module]
 * 역할: 
 * 1. 사용자 입력을 검증하여 '보호된 영역(HomeActivity)'으로의 접근을 제어합니다.
 * 2. 계정이 없는 사용자를 위해 회원가입 경로(JoinActivity)를 제공합니다.
 */
class MainActivity : AppCompatActivity() {

    // [Architecture: ViewBinding]
    // XML 위젯에 안전하게 접근하기 위한 바인딩 객체입니다.
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 레이아웃 바인딩 초기화 및 화면 설정
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**
         * [Event 1: 로그인 버튼 클릭 리스너]
         * 목적: 입력된 이메일을 확인하고 사용자 이름을 추출하여 홈 화면으로 전달합니다.
         */
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()

            // [Validation: 유효성 검사]
            if (email.isEmpty()) {
                // 전공자다운 방어적 프로그래밍: 빈 입력값에 대한 예외 처리
                Toast.makeText(this, "이메일을 입력해 주세요!", Toast.LENGTH_SHORT).show()
            } else {
                /**
                 * [Logic: 데이터 전달 및 화면 전환]
                 * 피그마 설계안의 '개인화된 경험'을 위해 이메일에서 아이디 부분을 추출합니다.
                 * 예: "test@email.com" -> "test" 님 환영합니다!
                 */
                val extractedName = email.split("@")[0]

                val intent = Intent(this, HomeActivity::class.java).apply {
                    // 키-값(Key-Value) 구조로 데이터를 바구니(Intent)에 담습니다.
                    putExtra("USER_NAME", extractedName)
                }
                
                startActivity(intent)
                
                // [Stack Management] 로그인 성공 후에는 로그인 창으로 다시 돌아올 필요가 없으므로 종료합니다.
                finish() 
                
                Toast.makeText(this, "${extractedName}님, 반갑습니다!", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * [Event 2: 회원가입 링크 클릭 리스너]
         * 목적: 회원가입 화면(JoinActivity)으로 사용자를 인도합니다.
         */
        binding.tvSignUp.setOnClickListener {
            val intent = Intent(this, JoinActivity::class.java)
            startActivity(intent)
            // 가입 후 다시 로그인 화면으로 복귀가 가능해야 하므로 finish()는 생략합니다.
        }
    }
}