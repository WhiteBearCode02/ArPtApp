package com.example.arptapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.databinding.ActivityJoinBinding
import com.example.arptapp.viewmodel.LoginState
import com.example.arptapp.viewmodel.LoginViewModel
import kotlinx.coroutines.launch

class JoinActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityJoinBinding
    private val loginViewModel: LoginViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJoinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupListeners()
        observeSignUpState()
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
        val email = binding.etJoinEmail.text.toString().trim()
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
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.etJoinEmail.error = "올바른 이메일 형식을 입력해주세요"
                binding.etJoinEmail.requestFocus()
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
        
        loginViewModel.signUp(email, password)
    }
    
    private fun observeSignUpState() {
        lifecycleScope.launch {
            loginViewModel.state.collect { state ->
                when (state) {
                    is LoginState.SignedUp -> {
                        Toast.makeText(this@JoinActivity, "이메일 인증 후 로그인해주세요.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    is LoginState.Authenticated -> finish()
                    is LoginState.Error -> Toast.makeText(this@JoinActivity, state.message, Toast.LENGTH_SHORT).show()
                    else -> Unit
                }
            }
        }
    }
    
}
