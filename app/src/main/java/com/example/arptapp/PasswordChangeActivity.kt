package com.example.arptapp

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.remote.SupabaseAuthRepository
import com.example.arptapp.databinding.ActivityPasswordChangeBinding
import kotlinx.coroutines.launch

class PasswordChangeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPasswordChangeBinding
    private val authRepository = SupabaseAuthRepository()
    private var verifiedPassword: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordChangeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarPassword.setNavigationOnClickListener { finish() }
        binding.btnVerifyPassword.setOnClickListener { verifyPassword() }
        binding.btnSavePassword.setOnClickListener { savePassword() }
        showVerificationStep()
    }

    override fun onStop() {
        super.onStop()
        binding.etVerifyPassword.text?.clear()
        binding.etNewPassword.text?.clear()
        binding.etConfirmPassword.text?.clear()
        showVerificationStep()
    }

    private fun showVerificationStep() {
        binding.verificationStep.visibility = View.VISIBLE
        binding.changeStep.visibility = View.GONE
        verifiedPassword = null
    }

    private fun verifyPassword() {
        val password = binding.etVerifyPassword.text?.toString().orEmpty()
        binding.tilVerifyPassword.error = null
        if (password.isBlank()) {
            binding.tilVerifyPassword.error = "현재 비밀번호를 입력해 주세요."
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            authRepository.verifyCurrentPassword(password)
                .onSuccess {
                    verifiedPassword = password
                    binding.etVerifyPassword.text?.clear()
                    binding.verificationStep.visibility = View.GONE
                    binding.changeStep.visibility = View.VISIBLE
                }
                .onFailure { error ->
                    binding.tilVerifyPassword.error = if (error.message.orEmpty().contains("Google")) {
                        error.message
                    } else {
                        "비밀번호가 맞지 않거나 확인할 수 없습니다. 다시 입력해 주세요."
                    }
                }
            setLoading(false)
        }
    }

    private fun savePassword() {
        val currentPassword = verifiedPassword ?: run {
            showVerificationStep()
            return
        }
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()
        val confirmation = binding.etConfirmPassword.text?.toString().orEmpty()
        binding.tilNewPassword.error = null
        binding.tilConfirmPassword.error = null
        when {
            newPassword.length < 8 -> binding.tilNewPassword.error = "8자 이상 입력해 주세요."
            newPassword == currentPassword -> binding.tilNewPassword.error = "현재 비밀번호와 다른 비밀번호를 입력해 주세요."
            newPassword != confirmation -> binding.tilConfirmPassword.error = "새 비밀번호가 일치하지 않습니다."
            else -> {
                setLoading(true)
                lifecycleScope.launch {
                    authRepository.updatePassword(currentPassword, newPassword)
                        .onSuccess {
                            verifiedPassword = null
                            binding.etNewPassword.text?.clear()
                            binding.etConfirmPassword.text?.clear()
                            Toast.makeText(this@PasswordChangeActivity, "비밀번호를 변경했습니다.", Toast.LENGTH_LONG).show()
                            finish()
                        }
                        .onFailure {
                            verifiedPassword = null
                            binding.etNewPassword.text?.clear()
                            binding.etConfirmPassword.text?.clear()
                            showVerificationStep()
                            Toast.makeText(this@PasswordChangeActivity, "변경하지 못했습니다. 현재 비밀번호를 다시 확인해 주세요.", Toast.LENGTH_LONG).show()
                        }
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressPassword.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnVerifyPassword.isEnabled = !loading
        binding.btnSavePassword.isEnabled = !loading
    }
}
