package com.example.arptapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.arptapp.data.preferences.UserSettings
import com.example.arptapp.data.preferences.UserSettingsRepository
import com.example.arptapp.data.remote.AppSession
import com.example.arptapp.data.remote.SupabaseAuthRepository
import com.example.arptapp.databinding.ActivityProfileBinding
import com.example.arptapp.utils.AlarmHelper
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.util.Locale

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private val authRepository = SupabaseAuthRepository()
    private val settingsRepository by lazy { UserSettingsRepository(this) }
    private var session: AppSession? = null
    private var reminderHour = 20
    private var reminderMinute = 0

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            binding.switchReminder.isChecked = false
            Toast.makeText(this, "알림 권한이 없어 운동 알림을 껐습니다.", Toast.LENGTH_LONG).show()
        }
        savePersonalSettings(checkNotificationPermission = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarProfile.setNavigationOnClickListener { finish() }
        binding.cardReminderTime.setOnClickListener { showTimePicker() }
        binding.btnSavePreferences.setOnClickListener { savePersonalSettings() }
        binding.btnUpdateEmail.setOnClickListener { updateEmail() }
        binding.btnUpdatePassword.setOnClickListener { updatePassword() }

        loadProfile()
    }

    private fun loadProfile() {
        setLoading(true)
        lifecycleScope.launch {
            authRepository.restoreSession()
                .onSuccess { restoredSession ->
                    if (restoredSession == null) {
                        showMessage("로그인 정보가 없습니다. 다시 로그인해 주세요.")
                        finish()
                        return@onSuccess
                    }
                    session = restoredSession
                    settingsRepository.activateUser(restoredSession.userId)
                    bindAccount(restoredSession)
                    bindSettings(settingsRepository.getSettings(restoredSession.userId))
                    setLoading(false)
                }
                .onFailure { error ->
                    showMessage(error.userMessage("설정을 불러오지 못했습니다."))
                    setLoading(false)
                }
        }
    }

    private fun bindAccount(session: AppSession) {
        binding.tvProfileEmailSummary.text = session.email
        binding.etProfileEmail.setText(session.email)
        binding.tilCurrentPassword.visibility =
            if (session.requiresCurrentPassword) View.VISIBLE else View.GONE
        binding.tvPasswordGuide.text = if (session.requiresCurrentPassword) {
            "안전을 위해 현재 비밀번호를 확인한 뒤 변경합니다."
        } else {
            "Google 로그인 계정에 앱 전용 비밀번호를 새로 설정할 수 있습니다."
        }
    }

    private fun bindSettings(settings: UserSettings) {
        binding.etProfileNickname.setText(settings.nickname)
        binding.etProfileHeight.setText(settings.heightCm.toInputText())
        binding.etProfileWeight.setText(settings.weightKg.toInputText())
        binding.etMuscleMass.setText(settings.skeletalMuscleMassKg.toInputText())
        binding.etBodyFat.setText(settings.bodyFatPercentage.toInputText())
        binding.switchReminder.isChecked = settings.reminderEnabled
        reminderHour = settings.reminderHour
        reminderMinute = settings.reminderMinute
        updateReminderTimeText()
    }

    private fun showTimePicker() {
        val picker = MaterialTimePicker.Builder()
            .setTitleText("운동 알림 시간")
            .setTimeFormat(if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
            .setHour(reminderHour)
            .setMinute(reminderMinute)
            .build()
        picker.addOnPositiveButtonClickListener {
            reminderHour = picker.hour
            reminderMinute = picker.minute
            binding.switchReminder.isChecked = true
            updateReminderTimeText()
        }
        picker.show(supportFragmentManager, "workout_reminder_time")
    }

    private fun updateReminderTimeText() {
        binding.tvReminderTime.text = String.format(
            Locale.KOREA,
            "매일 %02d:%02d",
            reminderHour,
            reminderMinute
        )
    }

    private fun savePersonalSettings(checkNotificationPermission: Boolean = true) {
        val currentSession = session ?: return
        clearBodyErrors()

        val nickname = binding.etProfileNickname.text?.toString()?.trim().orEmpty()
        if (nickname.length !in 2..20) {
            binding.tilNickname.error = "닉네임은 2~20자로 입력해 주세요."
            return
        }

        val height = requiredNumber(binding.etProfileHeight.text?.toString(), 80f, 250f) {
            binding.tilHeight.error = "키는 80~250cm 범위로 입력해 주세요."
        } ?: return
        val weight = requiredNumber(binding.etProfileWeight.text?.toString(), 20f, 350f) {
            binding.tilWeight.error = "몸무게는 20~350kg 범위로 입력해 주세요."
        } ?: return
        val muscleMass = optionalNumber(binding.etMuscleMass.text?.toString(), 0f, 200f) {
            binding.tilMuscleMass.error = "골격근량은 0~200kg 범위로 입력해 주세요."
        }
        if (binding.tilMuscleMass.error != null) return
        val bodyFat = optionalNumber(binding.etBodyFat.text?.toString(), 0f, 75f) {
            binding.tilBodyFat.error = "체지방률은 0~75% 범위로 입력해 주세요."
        }
        if (binding.tilBodyFat.error != null) return

        if (checkNotificationPermission && binding.switchReminder.isChecked && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        val settings = UserSettings(
            nickname = nickname,
            heightCm = height,
            weightKg = weight,
            skeletalMuscleMassKg = muscleMass,
            bodyFatPercentage = bodyFat,
            reminderEnabled = binding.switchReminder.isChecked,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute
        )

        setLoading(true)
        lifecycleScope.launch {
            runCatching {
                settingsRepository.save(currentSession.userId, settings)
                if (settings.reminderEnabled) {
                    AlarmHelper.scheduleDailyReminder(this@ProfileActivity, reminderHour, reminderMinute)
                } else {
                    AlarmHelper.cancelDailyReminder(this@ProfileActivity)
                }
            }.onSuccess {
                showMessage("개인 설정과 운동 알림을 저장했습니다.")
            }.onFailure { error ->
                showMessage(error.userMessage("설정을 저장하지 못했습니다."))
            }
            setLoading(false)
        }
    }

    private fun updateEmail() {
        val oldEmail = session?.email ?: return
        val newEmail = binding.etProfileEmail.text?.toString()?.trim().orEmpty()
        binding.tilProfileEmail.error = null
        when {
            !android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() -> {
                binding.tilProfileEmail.error = "올바른 이메일 주소를 입력해 주세요."
            }
            newEmail.equals(oldEmail, ignoreCase = true) -> {
                binding.tilProfileEmail.error = "현재 이메일과 동일합니다."
            }
            else -> {
                setLoading(true)
                lifecycleScope.launch {
                    authRepository.updateEmail(newEmail)
                        .onSuccess {
                            showMessage("인증 메일을 보냈습니다. 메일의 변경 링크를 확인해 주세요.")
                        }
                        .onFailure { error ->
                            showMessage(error.userMessage("이메일 변경을 요청하지 못했습니다."))
                        }
                    setLoading(false)
                }
            }
        }
    }

    private fun updatePassword() {
        val currentSession = session ?: return
        val currentPassword = binding.etCurrentPassword.text?.toString().orEmpty()
        val newPassword = binding.etNewPassword.text?.toString().orEmpty()
        val confirmation = binding.etConfirmPassword.text?.toString().orEmpty()
        clearPasswordErrors()

        when {
            currentSession.requiresCurrentPassword && currentPassword.isBlank() -> {
                binding.tilCurrentPassword.error = "현재 비밀번호를 입력해 주세요."
            }
            newPassword.length < 8 -> {
                binding.tilNewPassword.error = "새 비밀번호는 8자 이상이어야 합니다."
            }
            newPassword != confirmation -> {
                binding.tilConfirmPassword.error = "새 비밀번호가 일치하지 않습니다."
            }
            currentPassword.isNotEmpty() && currentPassword == newPassword -> {
                binding.tilNewPassword.error = "현재 비밀번호와 다른 비밀번호를 입력해 주세요."
            }
            else -> {
                setLoading(true)
                lifecycleScope.launch {
                    authRepository.updatePassword(currentPassword, newPassword)
                        .onSuccess {
                            binding.etCurrentPassword.text?.clear()
                            binding.etNewPassword.text?.clear()
                            binding.etConfirmPassword.text?.clear()
                            showMessage("Supabase 계정 비밀번호를 변경했습니다.")
                        }
                        .onFailure { error ->
                            showMessage(error.userMessage("비밀번호를 변경하지 못했습니다."))
                        }
                    setLoading(false)
                }
            }
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun requiredNumber(
        rawValue: String?,
        minimum: Float,
        maximum: Float,
        onInvalid: () -> Unit
    ): Float? {
        val value = rawValue.toNormalizedFloatOrNull()
        if (value == null || value !in minimum..maximum) onInvalid()
        return value?.takeIf { it in minimum..maximum }
    }

    private fun optionalNumber(
        rawValue: String?,
        minimum: Float,
        maximum: Float,
        onInvalid: () -> Unit
    ): Float? {
        if (rawValue.isNullOrBlank()) return null
        val value = rawValue.toNormalizedFloatOrNull()
        if (value == null || value !in minimum..maximum) onInvalid()
        return value?.takeIf { it in minimum..maximum }
    }

    private fun clearBodyErrors() {
        binding.tilNickname.error = null
        binding.tilHeight.error = null
        binding.tilWeight.error = null
        binding.tilMuscleMass.error = null
        binding.tilBodyFat.error = null
    }

    private fun clearPasswordErrors() {
        binding.tilCurrentPassword.error = null
        binding.tilNewPassword.error = null
        binding.tilConfirmPassword.error = null
    }

    private fun setLoading(loading: Boolean) {
        binding.progressProfile.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSavePreferences.isEnabled = !loading
        binding.btnUpdateEmail.isEnabled = !loading
        binding.btnUpdatePassword.isEnabled = !loading
    }

    private fun Throwable.userMessage(fallback: String): String {
        val source = message.orEmpty()
        return when {
            source.contains("invalid login credentials", ignoreCase = true) ->
                "현재 비밀번호가 올바르지 않습니다."
            source.contains("email", ignoreCase = true) &&
                source.contains("already", ignoreCase = true) ->
                "이미 사용 중인 이메일입니다."
            source.contains("network", ignoreCase = true) ||
                source.contains("unable to resolve", ignoreCase = true) ->
                "네트워크 연결을 확인해 주세요."
            else -> fallback
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun String?.toNormalizedFloatOrNull(): Float? =
        this?.trim()?.replace(',', '.')?.toFloatOrNull()

    private fun Float?.toInputText(): String = when {
        this == null -> ""
        this % 1f == 0f -> toInt().toString()
        else -> toString()
    }
}
