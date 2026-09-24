package com.example.arptapp.data.remote

import com.example.arptapp.data.preferences.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserPreferencesRepositoryTest {

    @Test
    fun cloudReminderPreferencesReplaceOnlyReminderFields() {
        val local = UserSettings(
            nickname = "coach",
            heightCm = 175f,
            weightKg = 70f,
            reminderEnabled = true,
            reminderHour = 20,
            reminderMinute = 0
        )

        val merged = local.withCloudReminderPreferences(
            CloudReminderPreferences(enabled = false, hour = 7, minute = 30)
        )

        assertEquals("coach", merged.nickname)
        assertEquals(175f, merged.heightCm)
        assertEquals(70f, merged.weightKg)
        assertFalse(merged.reminderEnabled)
        assertEquals(7, merged.reminderHour)
        assertEquals(30, merged.reminderMinute)
    }

    @Test
    fun invalidCloudTimeIsClampedBeforeLocalScheduling() {
        val merged = UserSettings().withCloudReminderPreferences(
            CloudReminderPreferences(enabled = true, hour = 99, minute = -5)
        )

        assertEquals(23, merged.reminderHour)
        assertEquals(0, merged.reminderMinute)
    }
}
