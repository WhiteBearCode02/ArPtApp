package com.example.arptapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.userSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_settings"
)

data class UserSettings(
    val nickname: String = "",
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val skeletalMuscleMassKg: Float? = null,
    val bodyFatPercentage: Float? = null,
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0
)

class UserSettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.userSettingsDataStore

    fun settings(userId: String): Flow<UserSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { preferences -> toUserSettings(preferences, SettingsKeys(userId)) }

    suspend fun getSettings(userId: String): UserSettings = settings(userId).first()

    suspend fun getActiveSettings(): UserSettings? {
        val preferences = dataStore.data.first()
        val userId = preferences[ACTIVE_USER_ID] ?: return null
        return toUserSettings(preferences, SettingsKeys(userId))
    }

    suspend fun activateUser(userId: String) {
        dataStore.edit { preferences -> preferences[ACTIVE_USER_ID] = userId }
    }

    suspend fun save(userId: String, settings: UserSettings) {
        val keys = SettingsKeys(userId)
        dataStore.edit { preferences ->
            preferences[ACTIVE_USER_ID] = userId
            preferences[keys.nickname] = settings.nickname
            setOptionalFloat(preferences, keys.heightCm, settings.heightCm)
            setOptionalFloat(preferences, keys.weightKg, settings.weightKg)
            setOptionalFloat(preferences, keys.muscleMassKg, settings.skeletalMuscleMassKg)
            setOptionalFloat(preferences, keys.bodyFatPercentage, settings.bodyFatPercentage)
            preferences[keys.reminderEnabled] = settings.reminderEnabled
            preferences[keys.reminderHour] = settings.reminderHour
            preferences[keys.reminderMinute] = settings.reminderMinute
        }
    }

    private fun toUserSettings(preferences: Preferences, keys: SettingsKeys): UserSettings = UserSettings(
        nickname = preferences[keys.nickname].orEmpty(),
        heightCm = preferences[keys.heightCm],
        weightKg = preferences[keys.weightKg],
        skeletalMuscleMassKg = preferences[keys.muscleMassKg],
        bodyFatPercentage = preferences[keys.bodyFatPercentage],
        reminderEnabled = preferences[keys.reminderEnabled] ?: true,
        reminderHour = preferences[keys.reminderHour] ?: 20,
        reminderMinute = preferences[keys.reminderMinute] ?: 0
    )

    private fun setOptionalFloat(
        preferences: MutablePreferences,
        key: Preferences.Key<Float>,
        value: Float?
    ) {
        if (value == null) preferences.remove(key) else preferences[key] = value
    }

    private data class SettingsKeys(val userId: String) {
        private val prefix = "user_${userId}_"
        val nickname = stringPreferencesKey("${prefix}nickname")
        val heightCm = floatPreferencesKey("${prefix}height_cm")
        val weightKg = floatPreferencesKey("${prefix}weight_kg")
        val muscleMassKg = floatPreferencesKey("${prefix}skeletal_muscle_mass_kg")
        val bodyFatPercentage = floatPreferencesKey("${prefix}body_fat_percentage")
        val reminderEnabled = booleanPreferencesKey("${prefix}reminder_enabled")
        val reminderHour = intPreferencesKey("${prefix}reminder_hour")
        val reminderMinute = intPreferencesKey("${prefix}reminder_minute")
    }

    private companion object {
        val ACTIVE_USER_ID = stringPreferencesKey("active_user_id")
    }
}
