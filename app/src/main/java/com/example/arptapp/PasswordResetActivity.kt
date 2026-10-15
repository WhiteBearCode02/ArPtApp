package com.example.arptapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import com.example.arptapp.viewmodel.PasswordRecoveryViewModel
import com.example.arptapp.databinding.ActivityPasswordResetBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import androidx.activity.OnBackPressedCallback

class PasswordResetActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPasswordResetBinding
    private val recoveryViewModel: PasswordRecoveryViewModel by viewModels()
    private val authRepository get() = recoveryViewModel.repository
    private var recoveryReady = false
    private var recoveryJob: Job? = null
    private var passwordUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPasswordResetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarPasswordReset.setNavigationOnClickListener { returnToLogin() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = returnToLogin()
        })
        binding.btnSaveRecoveredPassword.setOnClickListener { saveNewPassword() }
        binding.btnRequestResetAgain.setOnClickListener {
            recoveryJob?.cancel()
            lifecycleScope.launch {
                authRepository.discardPasswordRecovery()
                startActivity(Intent(this@PasswordResetActivity, ForgotPasswordActivity::class.java))
                finish()
            }
        }
        handleRecoveryIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRecoveryIntent(intent)
    }

    private fun handleRecoveryIntent(intent: Intent) {
        passwordUpdateJob?.cancel()
        recoveryJob?.cancel() // an older callback must not enable a newer invalid link's form
        if (recoveryViewModel.isVerified(intent.dataString)) {
            recoveryReady = true
            binding.tvPasswordResetStatus.visibility = View.GONE
            binding.btnRequestResetAgain.visibility = View.GONE
            binding.resetForm.visibility = View.VISIBLE
            setLoading(false)
            return
        }
        recoveryViewModel.invalidate()
        recoveryReady = false
        binding.resetForm.visibility = View.GONE
        binding.btnRequestResetAgain.visibility = View.GONE
        binding.tvPasswordResetStatus.visibility = View.VISIBLE
        binding.tvPasswordResetStatus.text = getString(R.string.password_reset_link_checking)
        setLoading(true)

        recoveryJob = lifecycleScope.launch {
            authRepository.handlePasswordRecovery(intent)
                .onSuccess {
                    recoveryViewModel.markVerified(intent.dataString)
                    recoveryReady = true
                    binding.tvPasswordResetStatus.visibility = View.GONE
                    binding.resetForm.visibility = View.VISIBLE
                }
                .onFailure {
                    binding.tvPasswordResetStatus.text = getString(R.string.password_reset_link_invalid)
                    binding.btnRequestResetAgain.visibility = View.VISIBLE
                }
            setLoading(false)
        }
    }

    private fun saveNewPassword() {
        if (!recoveryReady) return
        val version = authRepository.passwordRecoveryAuthorizationVersion() ?: run {
            recoveryReady = false
            setLoading(false)
            binding.tvPasswordResetStatus.text = getString(R.string.password_reset_link_invalid)
            binding.tvPasswordResetStatus.visibility = View.VISIBLE
            binding.resetForm.visibility = View.GONE
            binding.btnRequestResetAgain.visibility = View.VISIBLE
            return
        }
        val password = binding.etRecoveredPassword.text?.toString().orEmpty()
        val confirmation = binding.etRecoveredPasswordConfirm.text?.toString().orEmpty()
        binding.tilRecoveredPassword.error = null
        binding.tilRecoveredPasswordConfirm.error = null

        when {
            password.length < 8 -> binding.tilRecoveredPassword.error = "8자 이상 입력해 주세요."
            password != confirmation ->
                binding.tilRecoveredPasswordConfirm.error = "새 비밀번호가 일치하지 않습니다."
            else -> {
                setLoading(true)
                passwordUpdateJob = lifecycleScope.launch {
                    authRepository.updateRecoveredPassword(password, version)
                        .onSuccess {
                            returnToLogin("비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.")
                        }
                        .onFailure { error ->
                            val message = if (error.message.orEmpty().contains("weak", ignoreCase = true)) {
                                "더 안전한 비밀번호를 입력해 주세요."
                            } else {
                                "비밀번호를 변경하지 못했습니다. 재설정 링크를 다시 요청해 주세요."
                            }
                            Toast.makeText(this@PasswordResetActivity, message, Toast.LENGTH_LONG).show()
                        }
                    setLoading(false)
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressPasswordReset.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSaveRecoveredPassword.isEnabled = !loading && recoveryReady
        binding.etRecoveredPassword.isEnabled = !loading
        binding.etRecoveredPasswordConfirm.isEnabled = !loading
    }

    private fun returnToLogin(notice: String? = null) {
        recoveryJob?.cancel()
        passwordUpdateJob?.cancel()
        recoveryViewModel.invalidate()
        lifecycleScope.launch {
            authRepository.discardPasswordRecovery()
            startActivity(Intent(this@PasswordResetActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                notice?.let { putExtra(MainActivity.EXTRA_NOTICE, it) }
            })
            finish()
        }
    }
}
