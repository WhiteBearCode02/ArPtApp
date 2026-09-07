package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.example.arptapp.databinding.ActivityMainBinding
import com.example.arptapp.viewmodel.LoginState
import com.example.arptapp.viewmodel.LoginViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val loginViewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeLoginState()
        if (intent.data?.scheme == "arptapp") loginViewModel.handleAuthCallback(intent)
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
                    
                    loginViewModel.signIn(email, password)
                }
            }
        }

        binding.btnGoogleLogin.setOnClickListener {
            loginViewModel.signInWithGoogle()
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

    private fun observeLoginState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                loginViewModel.state.collect { state ->
                    when (state) {
                        LoginState.Idle, LoginState.Loading -> Unit
                        is LoginState.Authenticated -> navigateForSession(state.session)
                        is LoginState.SignedUp -> Toast.makeText(
                            this@MainActivity,
                            "회원가입이 완료되었습니다. 이메일 인증 후 로그인해 주세요.",
                            Toast.LENGTH_LONG
                        ).show()
                        is LoginState.Error -> {
                            binding.tilPassword.error = state.message
                            Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun navigateForSession(session: com.example.arptapp.data.remote.AppSession) {
        val destination = if (session.isAdmin) AdminDashboardActivity::class.java else HomeActivity::class.java
        startActivity(Intent(this, destination).apply {
            putExtra("USER_ID", session.userId)
            putExtra("USER_EMAIL", session.email)
            putExtra("IS_ADMIN", session.isAdmin)
        })
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.scheme == "arptapp") loginViewModel.handleAuthCallback(intent)
    }
}
