package com.example.arptapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.arptapp.databinding.ActivityJoinBinding

class JoinActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityJoinBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupListeners()
    }
    
    private fun setupListeners() {
        // 가입 완료 버튼 클릭
        binding.btnJoinSubmit.setOnClickListener {
            handleJoinSubmit()
        }
        
        // 로그인으로 돌아가기 클릭
        binding.tvBackToLogin.setOnClickListener {
            finish() // 현재 액티비티 종료하고 로그인 화면으로 돌아감
        }
    }
    
    private fun handleJoinSubmit() {
        val name = binding.etJoinName.text.toString().trim()
        val password = binding.etJoinPassword.text.toString().trim()
        
        // 유효성 검사
        when {
            name.isEmpty() -> {
                binding.etJoinName.error = "이름을 입력해주세요"
                binding.etJoinName.requestFocus()
                return
            }
            name.length < 2 -> {
                binding.etJoinName.error = "이름은 2자 이상이어야 합니다"
                binding.etJoinName.requestFocus()
                return
            }
            password.isEmpty() -> {
                binding.etJoinPassword.error = "비밀번호를 입력해주세요"
                binding.etJoinPassword.requestFocus()
                return
            }
            password.length < 6 -> {
                binding.etJoinPassword.error = "비밀번호는 6자 이상이어야 합니다"
                binding.etJoinPassword.requestFocus()
                return
            }
        }
        
        // 회원가입 처리
        if (registerUser(name, password)) {
            Toast.makeText(
                this,
                "회원가입이 완료되었습니다! 로그인해주세요.",
                Toast.LENGTH_SHORT
            ).show()
            
            // 로그인 화면으로 돌아가기
            finish()
        } else {
            Toast.makeText(
                this,
                "이미 존재하는 사용자입니다.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    /**
     * 사용자 등록 (SharedPreferences 사용)
     */
    private fun registerUser(name: String, password: String): Boolean {
        val sharedPref = getSharedPreferences("ArPtAppPrefs", MODE_PRIVATE)
        
        // 이미 등록된 사용자인지 확인
        val existingName = sharedPref.getString("user_name", null)
        if (existingName != null) {
            return false // 이미 사용자가 존재함
        }
        
        // 사용자 정보 저장
        with(sharedPref.edit()) {
            putString("user_name", name)
            putString("user_password", password)
            putBoolean("is_registered", true)
            putLong("registration_date", System.currentTimeMillis())
            apply()
        }
        
        return true
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}