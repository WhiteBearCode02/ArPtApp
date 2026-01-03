package com.example.arptapp.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.arptapp.MainActivity
import com.example.arptapp.R

/**
 * 예약된 시간에 신호를 받아 상단바 알림을 띄우는 리시버입니다.
 */
class AlarmReceiver : BroadcastReceiver() {

    private val CHANNEL_ID = "AR_PT_REMINDER"

    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 안드로이드 8.0 이상은 채널 설정 필수
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "운동 리마인더", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        // 알림 클릭 시 앱의 메인 화면으로 이동하도록 설정
        val mainIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 알림 내용 구성
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher) // 앱 아이콘 사용
            .setContentTitle("AR PT COACH")
            .setContentText("오늘 운동하셨나요? AI 코치가 기다리고 있어요!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // 클릭 시 알림 삭제
            .build()

        notificationManager.notify(1, notification)
    }
}