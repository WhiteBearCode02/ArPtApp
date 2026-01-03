package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // 로그인 버튼
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            // 입력값 검증
            when {
                email.isEmpty() -> {
                    binding.tilEmail.error = "이메일을 입력해주세요"
                    binding.etEmail.requestFocus()
                }
                !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                    binding.tilEmail.error = "올바른 이메일 형식이 아닙니다"
                    binding.etEmail.requestFocus()
                }
                password.isEmpty() -> {
                    binding.tilEmail.error = null
                    binding.tilPassword.error = "비밀번호를 입력해주세요"
                    binding.etPassword.requestFocus()
                }
                password.length < 6 -> {
                    binding.tilPassword.error = "비밀번호는 6자 이상이어야 합니다"
                    binding.etPassword.requestFocus()
                }
                else -> {
                    // 모든 검증 통과
                    binding.tilEmail.error = null
                    binding.tilPassword.error = null
                    
                    Toast.makeText(this, "로그인 성공!", Toast.LENGTH_SHORT).show()
                    
                    // HomeActivity로 이동
                    val intent = Intent(this, HomeActivity::class.java)
                    intent.putExtra("USER_EMAIL", email)
                    startActivity(intent)
                    finish()
                }
            }
        }

        // 회원가입 버튼
        binding.btnSignUp.setOnClickListener {
            val intent = Intent(this, JoinActivity::class.java)
            startActivity(intent)
        }

        // 비밀번호 찾기
        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "비밀번호 찾기 기능은 준비 중입니다", Toast.LENGTH_SHORT).show()
        }

        // 도움말
        binding.tvHelp.setOnClickListener {
            Toast.makeText(this, "도움말 화면으로 이동합니다", Toast.LENGTH_SHORT).show()
        }
    }
}
