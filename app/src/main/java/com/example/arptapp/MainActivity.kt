package com.example.arptapp

// [Imports: 안드로이드 프레임워크 및 화면 전환 도구]
import android.content.Intent // 화면 간 이동을 담당하는 인텐트 객체
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityMainBinding

/**
 * [ArPtApp - Authentication Module]
 * 역할: 
 * 1. 사용자의 이메일 기반 로그인을 처리합니다.
 * 2. 신규 사용자를 위한 회원가입 화면(JoinActivity)으로의 진입점을 제공합니다.
 * 3. 인증 성공 시 앱의 메인 허브인 HomeActivity로 사용자를 인도합니다.
 */
class MainActivity : AppCompatActivity() {

    // [Architecture: ViewBinding]
    // XML 컴포넌트(Button, EditText 등)를 타입 안정성을 보장하며 참조하기 위한 바인딩 객체
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 레이아웃 바인딩 초기화 및 화면 설정
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /**
         * [Event 1: 로그인 버튼 클릭 리스너]
         * 목적: 입력된 정보를 검증하고 메인 허브(HomeActivity)로 전환합니다.
         */
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()

            // [Validation: 유효성 검사]
            if (email.isEmpty()) {
                // 이메일이 입력되지 않았을 경우 사용자에게 알림 제공
                Toast.makeText(this, "이메일을 입력해 주세요!", Toast.LENGTH_SHORT).show()
            } else {
                // [Logic] 로그인 성공 시 대시보드가 아닌 '메인 허브(HomeActivity)'로 전환합니다.
                // Intent: 현재 Context(this)에서 목적지 클래스(HomeActivity)를 명시함
                val intent = Intent(this, HomeActivity::class.java)
                
                // 화면 전환 실행
                startActivity(intent)
                
                // [Stack Management] 현재 로그인 화면을 종료하여 뒤로가기 시 다시 나타나지 않게 함
                finish() 
                
                Toast.makeText(this, "${email}님, 환영합니다!", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * [Event 2: 회원가입 링크 클릭 리스너]
         * 목적: 계정이 없는 사용자를 위해 회원가입 화면(JoinActivity)을 호출합니다.
         */
        binding.tvSignUp.setOnClickListener {
            // JoinActivity를 호출하는 인텐트 생성
            val intent = Intent(this, JoinActivity::class.java)
            startActivity(intent)
            // 가입 후 다시 로그인으로 돌아올 수 있어야 하므로 finish()는 호출하지 않습니다.
        }
    }
}