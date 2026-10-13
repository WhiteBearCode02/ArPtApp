package com.example.arptapp.ui.profile

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.CalendarContract
import com.example.arptapp.R

/**
 * 사용자 동작에 따른 외부 캘린더 화면 연결만 담당하는 플랫폼 어댑터.
 *
 * READ/WRITE_CALENDAR 권한, Google API, 일정 조회/직접 저장은 사용하지 않는다.
 * 일정 추가는 외부 편집 화면에 값만 채우며 사용자가 최종 저장해야 한다.
 * Google Calendar 우선 실행 후 처리 가능한 다른 캘린더 앱으로 fallback 한다.
 */
class ProfileCalendarNavigator(private val activity: Activity) {
    fun openAt(reminderTime: ReminderTime): Boolean {
        val uri = CalendarContract.CONTENT_URI.buildUpon()
            .appendPath("time")
            .appendPath(reminderTime.nextOccurrenceMillis().toString())
            .build()
        return launch(Intent(Intent.ACTION_VIEW, uri))
    }

    fun openReminderEditor(reminderTime: ReminderTime): Boolean {
        val beginAt = reminderTime.nextOccurrenceMillis()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginAt)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginAt + EVENT_DURATION_MS)
            putExtra(CalendarContract.Events.TITLE, activity.getString(R.string.calendar_event_title))
            putExtra(CalendarContract.Events.DESCRIPTION, activity.getString(R.string.calendar_event_description))
            putExtra(CalendarContract.Events.RRULE, "FREQ=DAILY")
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        }
        return launch(intent)
    }

    private fun launch(baseIntent: Intent): Boolean =
        tryLaunch(Intent(baseIntent).setPackage(GOOGLE_CALENDAR_PACKAGE)) || tryLaunch(baseIntent)

    private fun tryLaunch(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        // 기기 정책 등으로 외부 Activity 실행이 제한돼도 개인설정 화면을 종료시키지 않는다.
        false
    }

    private companion object {
        const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"
        const val EVENT_DURATION_MS = 60L * 60L * 1000L
    }
}
