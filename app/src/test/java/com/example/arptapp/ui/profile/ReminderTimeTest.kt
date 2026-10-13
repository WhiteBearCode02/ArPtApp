package com.example.arptapp.ui.profile

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderTimeTest {
    private val seoul = TimeZone.getTimeZone("Asia/Seoul")

    @Test fun upcomingTimeUsesTodayAndClearsSeconds() {
        val now = instant(2026, 10, 13, 19, 12, 34, 567)
        assertEquals(
            instant(2026, 10, 13, 20, 0),
            ReminderTime(20, 0).nextOccurrenceMillis(now, seoul)
        )
    }

    @Test fun pastTimeUsesTomorrow() {
        val now = instant(2026, 10, 13, 20, 1)
        assertEquals(
            instant(2026, 10, 14, 20, 0),
            ReminderTime(20, 0).nextOccurrenceMillis(now, seoul)
        )
    }

    @Test fun equalTimeUsesTomorrow() {
        val now = instant(2026, 10, 13, 20, 0)
        assertEquals(
            instant(2026, 10, 14, 20, 0),
            ReminderTime(20, 0).nextOccurrenceMillis(now, seoul)
        )
    }

    @Test fun midnightRollsAcrossYearBoundary() {
        val now = instant(2026, 12, 31, 23, 59)
        assertEquals(
            instant(2027, 1, 1, 0, 0),
            ReminderTime(0, 0).nextOccurrenceMillis(now, seoul)
        )
    }

    @Test fun daylightSavingChangeKeepsSelectedLocalHour() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        val now = instant(2026, 3, 7, 21, 0, timeZone = newYork)
        assertEquals(
            instant(2026, 3, 8, 8, 0, timeZone = newYork),
            ReminderTime(8, 0).nextOccurrenceMillis(now, newYork)
        )
    }

    @Test fun normalizedStoredTimeIsSafeForPicker() {
        assertEquals(ReminderTime(0, 59), ReminderTime.normalized(-1, 65))
        assertEquals(ReminderTime(23, 0), ReminderTime.normalized(24, -1))
        assertEquals(ReminderTime(20, 0), ReminderTime.normalized(20, 0))
    }

    @Test fun dailyLabelHasFixedWidthHourAndMinute() {
        assertEquals("매일 08:05", ReminderTime(8, 5).dailyLabel())
        assertEquals("매일 20:00", ReminderTime(20, 0).dailyLabel())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHourCannotConstructValue() {
        ReminderTime(24, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMinuteCannotConstructValue() {
        ReminderTime(20, 60)
    }

    private fun instant(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
        millisecond: Int = 0,
        timeZone: TimeZone = seoul
    ): Long = Calendar.getInstance(timeZone).run {
        clear()
        set(year, month - 1, day, hour, minute, second)
        set(Calendar.MILLISECOND, millisecond)
        timeInMillis
    }
}
