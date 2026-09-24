package com.example.arptapp.data.remote

import com.example.arptapp.data.preferences.UserSettings
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.TimeZone

data class CloudReminderPreferences(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int
)

class UserPreferencesRepository {
    private val client get() = SupabaseClientProvider.client

    suspend fun getPreferences(userId: String): Result<CloudReminderPreferences?> = runCatching {
        client.from("user_preferences")
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList<PreferenceRow>()
            .singleOrNull()
            ?.toDomain()
    }

    suspend fun savePreferences(userId: String, settings: UserSettings): Result<Unit> = runCatching {
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

fun UserSettings.withCloudReminderPreferences(
    preferences: CloudReminderPreferences
): UserSettings = copy(
    reminderEnabled = preferences.enabled,
    reminderHour = preferences.hour.coerceIn(0, 23),
    reminderMinute = preferences.minute.coerceIn(0, 59)
)
