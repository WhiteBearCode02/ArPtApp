package com.example.arptapp.ui.profile

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * 선택한 일일 알림시각의 표시/다음 발생시각 계산을 담당하는 순수 값 객체.
 *
 * 시간대와 기준시각을 주입할 수 있어 자정/오늘 경과/시간대 경계를 JVM에서 검증할 수 있다.
 * 달력 앱과 기존 AlarmHelper가 사용하는 기기 현지시각 정책을 유지한다.
 */
data class ReminderTime(val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59) { "알림시각 범위를 벗어났습니다." }
    }

    fun dailyLabel(): String = String.format(Locale.KOREA, "매일 %02d:%02d", hour, minute)

    /** 현재시각과 동일한 순간은 이미 경과한 것으로 보아 다음 날을 반환한다. */
    fun nextOccurrenceMillis(
        nowMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long = Calendar.getInstance(timeZone).run {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_MONTH, 1)
        timeInMillis
    }

    companion object {
        /** 이전 저장 데이터가 범위를 벗어나더라도 TimePicker에 전달하기 전에 안전하게 보정한다. */
        fun normalized(hour: Int, minute: Int): ReminderTime =
            ReminderTime(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }
}
