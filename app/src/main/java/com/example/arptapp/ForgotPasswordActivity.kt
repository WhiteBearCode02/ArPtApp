package com.example.arptapp

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.remote.SupabaseAuthRepository
import com.example.arptapp.databinding.ActivityForgotPasswordBinding
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityForgotPasswordBinding
    private val authRepository = SupabaseAuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarForgotPassword.setNavigationOnClickListener { finish() }
        binding.etResetEmail.setText(intent.getStringExtra(EXTRA_EMAIL).orEmpty())
        binding.btnSendResetEmail.setOnClickListener { sendResetEmail() }
        binding.btnBackToLogin.setOnClickListener { finish() }
    }

    private fun sendResetEmail() {
        val email = binding.etResetEmail.text?.toString()?.trim().orEmpty()
        binding.tilResetEmail.error = null
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilResetEmail.error = "올바른 이메일 주소를 입력해 주세요."
            binding.etResetEmail.requestFocus()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            authRepository.requestPasswordReset(email)
                .onSuccess {
                    // 계정 존재 여부를 노출하지 않기 위해 항상 같은 완료 화면을 사용합니다.
                    binding.requestForm.visibility = View.GONE
                    binding.successState.visibility = View.VISIBLE
                    binding.tvResetEmailSent.text = getString(R.string.password_reset_email_sent, email)
                }
                .onFailure { error ->
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        error.toPasswordResetMessage(),
                        Toast.LENGTH_LONG
                    ).show()
                }
            setLoading(false)
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressForgotPassword.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSendResetEmail.isEnabled = !loading
        binding.etResetEmail.isEnabled = !loading
    }

    private fun Throwable.toPasswordResetMessage(): String {
        val source = message.orEmpty()
        return when {
            source.contains("rate", ignoreCase = true) || source.contains("too many", ignoreCase = true) ->
                "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
            source.contains("network", ignoreCase = true) || source.contains("resolve", ignoreCase = true) ->
                "네트워크 연결을 확인해 주세요."
            else -> "재설정 메일을 보내지 못했습니다. 잠시 후 다시 시도해 주세요."
        }
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}
