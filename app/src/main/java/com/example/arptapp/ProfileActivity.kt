package com.example.arptapp

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
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
import com.example.arptapp.core.coroutines.suspendResult
import com.example.arptapp.data.preferences.UserSettings
import com.example.arptapp.data.preferences.UserSettingsRepository
import com.example.arptapp.data.remote.AppSession
import com.example.arptapp.data.remote.SupabaseAuthRepository
import com.example.arptapp.data.remote.UserProfileRepository
import com.example.arptapp.data.remote.UserPreferencesRepository
import com.example.arptapp.data.repository.UserSettingsSyncRepository
import com.example.arptapp.databinding.ActivityProfileBinding
import com.example.arptapp.ui.profile.BodyProfileForm
import com.example.arptapp.ui.profile.ProfileCalendarNavigator
import com.example.arptapp.ui.profile.ReminderTime
import com.example.arptapp.utils.AlarmHelper
import kotlinx.coroutines.launch

/**
 * 개인설정 화면의 이벤트/인증된 계정 저장 흐름을 연결한다.
 * 신체정보 폼/검증과 캘린더 연결/시각 계산은 ui.profile 모듈에 위임한다.
 * 신체정보와 알림은 별도로 저장하며 외부 캘린더 편집은 앱 알림 저장과 독립적이다.
 */
class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private val authRepository = SupabaseAuthRepository()
    private val cloudProfileRepository = UserProfileRepository()
    private val cloudPreferencesRepository = UserPreferencesRepository()
    private val settingsRepository by lazy { UserSettingsRepository(this) }
    private val settingsSyncRepository by lazy {
        UserSettingsSyncRepository(settingsRepository, cloudProfileRepository, cloudPreferencesRepository)
    }
    private val bodyProfileForm by lazy { BodyProfileForm(binding) }
    private val calendarNavigator by lazy { ProfileCalendarNavigator(this) }
    private var session: AppSession? = null
    private var reminderTime = ReminderTime(20, 0)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            binding.switchReminder.isChecked = false
            Toast.makeText(this, "알림 권한이 없어 운동 알림을 껐습니다.", Toast.LENGTH_LONG).show()
        }
        saveReminderSettings(checkNotificationPermission = false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarProfile.setNavigationOnClickListener { finish() }
        binding.cardReminderTime.setOnClickListener { showTimePicker() }
        binding.btnSaveBodyInfo.setOnClickListener { saveBodyInfo() }
        binding.btnSaveReminder.setOnClickListener { saveReminderSettings() }
        binding.btnOpenGoogleCalendar.setOnClickListener { openCalendarAtReminderTime() }
        binding.btnCreateGoogleCalendarReminder.setOnClickListener { createCalendarReminder() }
        binding.btnUpdateEmail.setOnClickListener { updateEmail() }
        binding.btnUpdatePassword.setOnClickListener {
            startActivity(Intent(this, PasswordChangeActivity::class.java))
        }

        loadProfile()
    }

    private fun loadProfile() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                suspendResult {
                    // 화면 Intent 값이 아닌 복원된 인증 세션으로만 저장 대상 계정을 결정한다.
                    val restoredSession = authRepository.restoreSession().getOrThrow()
                        ?: return@suspendResult null
                    session = restoredSession
                    bindAccount(restoredSession)
                    settingsSyncRepository.synchronize(restoredSession.userId)
                }.onSuccess { settings ->
                    if (settings == null) {
                        showMessage("로그인 정보가 없습니다. 다시 로그인해 주세요.")
                        finish()
                    } else {
                        bindSettings(settings)
                    }
                }.onFailure { error ->
                    showMessage(error.userMessage("설정을 불러오지 못했습니다."))
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun bindAccount(session: AppSession) {
        binding.tvProfileEmailSummary.text = session.email
        binding.etProfileEmail.setText(session.email)
        binding.btnUpdatePassword.visibility =
            if (session.requiresCurrentPassword) View.VISIBLE else View.GONE
        binding.tvPasswordGuide.text = if (session.requiresCurrentPassword) {
            "비밀번호 변경 전에 현재 비밀번호로 본인을 확인합니다."
        } else {
            "Google 계정의 비밀번호는 Google 계정에서 관리해 주세요."
        }
    }

    private fun bindSettings(settings: UserSettings) {
        bodyProfileForm.bind(settings)
        binding.switchReminder.isChecked = settings.reminderEnabled
        reminderTime = ReminderTime.normalized(settings.reminderHour, settings.reminderMinute)
        updateReminderTimeText()
    }

    private fun showTimePicker() {
        TimePickerDialog(this, { _, hour, minute ->
            reminderTime = ReminderTime(hour, minute)
            binding.switchReminder.isChecked = true
            updateReminderTimeText()
        }, reminderTime.hour, reminderTime.minute,
            DateFormat.is24HourFormat(this)).show()
    }

    private fun updateReminderTimeText() {
        binding.tvReminderTime.text = reminderTime.dailyLabel()
    }

    private fun saveBodyInfo() {
        val currentSession = session ?: return
        val bodyProfile = bodyProfileForm.validatedValues() ?: return

        setLoading(true)
        lifecycleScope.launch {
            try {
                // 신체정보만 갱신하여 별도로 저장하는 알림 설정을 덮어쓰지 않는다.
                // 화면 종료에 따른 취소는 실패 메시지나 후속 클라우드 저장으로 이어지지 않는다.
                suspendResult {
                    val settings = settingsRepository.getSettings(currentSession.userId).copy(
                        nickname = bodyProfile.nickname,
                        heightCm = bodyProfile.heightCm,
                        weightKg = bodyProfile.weightKg,
                        skeletalMuscleMassKg = bodyProfile.skeletalMuscleMassKg,
                        bodyFatPercentage = bodyProfile.bodyFatPercentage
                    )
                    settingsRepository.save(currentSession.userId, settings)
                    settings
                }.onSuccess { settings ->
                    cloudProfileRepository.saveBodyProfile(currentSession.userId, settings)
                        .onSuccess {
                            showMessage("신체 정보를 기기와 Supabase에 저장했습니다.")
                        }
                        .onFailure { error ->
                            showMessage(error.userMessage("기기에는 저장했지만 Supabase 동기화에 실패했습니다."))
                        }
                }.onFailure { error ->
                    showMessage(error.userMessage("신체 정보를 기기에 저장하지 못했습니다."))
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun saveReminderSettings(checkNotificationPermission: Boolean = true) {
        val currentSession = session ?: return
        if (checkNotificationPermission && binding.switchReminder.isChecked && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        val enabled = binding.switchReminder.isChecked
        val hour = reminderTime.hour
        val minute = reminderTime.minute
        setLoading(true)
        lifecycleScope.launch {
            try {
                val settingsResult = suspendResult {
                    val settings = settingsRepository.getSettings(currentSession.userId).copy(
                        reminderEnabled = enabled,
                        reminderHour = hour,
                        reminderMinute = minute
                    )
                    settingsRepository.save(currentSession.userId, settings)
                    if (enabled) {
                        AlarmHelper.scheduleDailyReminder(this@ProfileActivity, hour, minute)
                    } else {
                        AlarmHelper.cancelDailyReminder(this@ProfileActivity)
                    }
                    settings
                }
                settingsResult.onFailure { error ->
                    showMessage(error.userMessage("알림 설정을 저장하지 못했습니다."))
                }
                settingsResult.getOrNull()?.let { settings ->
                    cloudPreferencesRepository.savePreferences(currentSession.userId, settings)
                        .onSuccess {
                            showMessage("알림 설정을 기기와 Supabase에 저장했습니다.")
                        }
                        .onFailure { error ->
                            showMessage(error.userMessage("기기에는 저장했지만 Supabase 동기화에 실패했습니다."))
                        }
                }
            } finally {
                setLoading(false)
            }
        }
    }

    private fun openCalendarAtReminderTime() {
        if (!calendarNavigator.openAt(reminderTime)) {
            showMessage(getString(R.string.calendar_app_not_found))
        }
    }

    private fun createCalendarReminder() {
        if (!calendarNavigator.openReminderEditor(reminderTime)) {
            showMessage(getString(R.string.calendar_app_not_found))
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
                    try {
                        authRepository.updateEmail(newEmail)
                            .onSuccess {
                                showMessage("인증 메일을 보냈습니다. 메일의 변경 링크를 확인해 주세요.")
                            }
                            .onFailure { error ->
                                showMessage(error.userMessage("이메일 변경을 요청하지 못했습니다."))
                            }
                    } finally {
                        setLoading(false)
                    }
                }
            }
        }
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun setLoading(loading: Boolean) {
        binding.progressProfile.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSaveBodyInfo.isEnabled = !loading
        binding.btnSaveReminder.isEnabled = !loading
        binding.switchReminder.isEnabled = !loading
        binding.cardReminderTime.isEnabled = !loading
        binding.btnOpenGoogleCalendar.isEnabled = !loading
        binding.btnCreateGoogleCalendarReminder.isEnabled = !loading
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
}
