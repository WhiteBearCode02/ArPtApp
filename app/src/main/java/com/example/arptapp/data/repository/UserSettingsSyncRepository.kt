package com.example.arptapp.data.repository

import com.example.arptapp.data.preferences.UserSettings
import com.example.arptapp.data.remote.withCloudBodyProfile
import com.example.arptapp.data.remote.withCloudReminderPreferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Home에서 하던 로컬/원격 설정 병합을 담당합니다. 화면·알람·Auth SDK를 알지 못합니다.
 *
 * 신체 수치의 원격 null은 로컬 값을 유지하고, 존재하는 원격 알림 설정은 로컬을 대체합니다.
 * 원격 조회 실패는 설정 부재가 아니므로 초기값을 원격에 덮어쓰지 않습니다.
 * 동일한 호출 순서와 userId를 사용하며 자동 재시도/추가 서버 기능은 없습니다.
 */
class UserSettingsSyncRepository(
    private val local: UserSettingsStore,
    private val profiles: BodyProfileSource,
    private val reminders: ReminderPreferencesSource
) {
    /** 반환 이후 화면에서 필요한 기기 알람 예약을 수행합니다. 호출자는 lifecycle 취소를 소유합니다. */
    suspend fun synchronize(userId: String): UserSettings {
        require(userId.isNotBlank()) { "사용자 식별자가 필요합니다." }
        currentCoroutineContext().ensureActive()
        local.activateUser(userId)
        val settings = local.getSettings(userId)
        val profile = profiles.getProfile(userId).getOrNull()
        var merged = profile?.let(settings::withCloudBodyProfile) ?: settings

        val preferenceResult = reminders.getPreferences(userId)
        currentCoroutineContext().ensureActive()
        if (preferenceResult.isSuccess) {
            val preferences = preferenceResult.getOrNull()
            if (preferences == null) {
                // 행이 정말 없을 때만 초기 설정을 생성합니다. 실패 시 로컬 설정으로 계속 사용합니다.
                reminders.savePreferences(userId, merged)
            } else {
                merged = merged.withCloudReminderPreferences(preferences)
            }
        }
        currentCoroutineContext().ensureActive()
        local.save(userId, merged)
        return merged
    }
}
