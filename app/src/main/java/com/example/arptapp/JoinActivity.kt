package com.example.arptapp

import android.os.Bundle
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.databinding.ActivityJoinBinding
import com.example.arptapp.data.remote.SignUpProfile
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
        
        val weight = readOptionalMeasurement(binding.etJoinWeight) ?: if (binding.etJoinWeight.text.isNullOrBlank()) null else return
        val muscle = readOptionalMeasurement(binding.etJoinMuscle) ?: if (binding.etJoinMuscle.text.isNullOrBlank()) null else return
        val fatMass = readOptionalMeasurement(binding.etJoinFatMass) ?: if (binding.etJoinFatMass.text.isNullOrBlank()) null else return
        val fatPercent = readOptionalMeasurement(binding.etJoinFatPercent, 100.0)
            ?: if (binding.etJoinFatPercent.text.isNullOrBlank()) null else return

        loginViewModel.signUp(email, password, SignUpProfile(name, weight, muscle, fatMass, fatPercent))
    }

    private fun readOptionalMeasurement(field: EditText, max: Double = 500.0): Double? {
        val input = field.text.toString().trim()
        if (input.isEmpty()) return null
        val value = input.toDoubleOrNull()
        if (value == null || !value.isFinite() || value <= 0.0 || value > max) {
            field.error = "0보다 크고 ${max.toInt()} 이하인 숫자를 입력해 주세요"
            field.requestFocus()
            return null
        }
        field.error = null
        return value
    }
    
    private fun observeSignUpState() {
        lifecycleScope.launch {
            loginViewModel.state.collect { state ->
                when (state) {
                    LoginState.Loading -> binding.btnJoinSubmit.isEnabled = false
                    is LoginState.SignedUp -> {
                        binding.btnJoinSubmit.isEnabled = true
                        Toast.makeText(this@JoinActivity, "가입이 완료됐습니다. 이메일 인증 링크를 확인해 주세요.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    is LoginState.Authenticated -> {
                        startActivity(Intent(this@JoinActivity, if (state.session.isAdmin) AdminDashboardActivity::class.java else HomeActivity::class.java).apply {
                            putExtra("USER_ID", state.session.userId)
                            putExtra("USER_EMAIL", state.session.email)
                            putExtra("IS_ADMIN", state.session.isAdmin)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        finish()
                    }
                    is LoginState.Error -> {
                        binding.btnJoinSubmit.isEnabled = true
                        Toast.makeText(this@JoinActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    else -> Unit
                }
            }
        }
    }
    
}
