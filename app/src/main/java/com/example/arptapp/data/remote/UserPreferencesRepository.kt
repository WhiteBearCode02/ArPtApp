package com.example.arptapp.data.remote

import com.example.arptapp.data.preferences.UserSettings
import com.example.arptapp.core.coroutines.suspendResult
import com.example.arptapp.data.repository.ReminderPreferencesSource
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.TimeZone

data class CloudReminderPreferences(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
)

/** 알림 설정의 원격 어댑터. 기기 알람 예약과 캘린더 실행은 UI/기기 계층에서 수행합니다. */
class UserPreferencesRepository : ReminderPreferencesSource {
    private val client get() = SupabaseClientProvider.client

    override suspend fun getPreferences(userId: String): Result<CloudReminderPreferences?> = suspendResult {
        client.from("user_preferences")
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList<PreferenceRow>()
            .singleOrNull()
            ?.toDomain()
    }

    override suspend fun savePreferences(userId: String, settings: UserSettings): Result<Unit> = suspendResult {
        client.from("user_preferences").upsert(
            PreferenceWriteRow(
                userId = userId,
                reminderEnabled = settings.reminderEnabled,
                reminderHour = settings.reminderHour.coerceIn(0, 23),
                reminderMinute = settings.reminderMinute.coerceIn(0, 59),
                timezone = TimeZone.getDefault().id.take(64)
            )
        ) {
            onConflict = "user_id"
        }
        Unit
    }

    @Serializable
    private data class PreferenceRow(
        @SerialName("reminder_enabled") val reminderEnabled: Boolean,
        @SerialName("reminder_hour") val reminderHour: Int,
        @SerialName("reminder_minute") val reminderMinute: Int
    ) {
        fun toDomain() = CloudReminderPreferences(
            enabled = reminderEnabled,
            hour = reminderHour,
            minute = reminderMinute
        )
    }

    @Serializable
    private data class PreferenceWriteRow(
        @SerialName("user_id") val userId: String,
        @SerialName("reminder_enabled") val reminderEnabled: Boolean,
        @SerialName("reminder_hour") val reminderHour: Int,
        @SerialName("reminder_minute") val reminderMinute: Int,
        val timezone: String
    )
}

/** 서버 값의 시간 범위를 보정한 뒤 알림 필드만 대체합니다. 신체정보는 변경하지 않습니다. */
fun UserSettings.withCloudReminderPreferences(
    preferences: CloudReminderPreferences
): UserSettings = copy(
    reminderEnabled = preferences.enabled,
    reminderHour = preferences.hour.coerceIn(0, 23),
    reminderMinute = preferences.minute.coerceIn(0, 59)
)
