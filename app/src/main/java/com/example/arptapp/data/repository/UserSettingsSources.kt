package com.example.arptapp.data.repository

import com.example.arptapp.data.preferences.UserSettings
import com.example.arptapp.data.remote.CloudBodyProfile
import com.example.arptapp.data.remote.CloudReminderPreferences

/** 설정 동기화가 필요한 로컬 기능만 공개하는 포트. DataStore 외 구현으로도 교체할 수 있습니다. */
interface UserSettingsStore {
    suspend fun activateUser(userId: String)
    suspend fun getSettings(userId: String): UserSettings
    suspend fun save(userId: String, settings: UserSettings)
}

/** 성공한 null은 프로필 없음, Result.failure는 조회 실패입니다. 둘을 혼동하면 안 됩니다. */
interface BodyProfileSource {
    suspend fun getProfile(userId: String): Result<CloudBodyProfile?>
    suspend fun saveBodyProfile(userId: String, settings: UserSettings): Result<Unit>
}

/** 원격 알림 설정 API. 실제 Android 알람 예약은 이 포트의 책임이 아닙니다. */
interface ReminderPreferencesSource {
    suspend fun getPreferences(userId: String): Result<CloudReminderPreferences?>
    suspend fun savePreferences(userId: String, settings: UserSettings): Result<Unit>
}
