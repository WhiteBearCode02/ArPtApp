package com.example.arptapp.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.*

/**
 * 시스템 알림 예약을 도와주는 유틸리티 클래스입니다.
 */
object AlarmHelper {

    fun setupDailyReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        
        // PendingIntent: 시스템이 나중에 우리 대신 실행할 인텐트
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 매일 오후 8시 설정
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 20) // 20시 (오후 8시)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // 설정된 시간이 이미 지났다면 다음 날부터 시작
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // 매일 반복 예약
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}