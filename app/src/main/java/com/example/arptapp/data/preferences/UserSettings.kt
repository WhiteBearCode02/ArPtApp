package com.example.arptapp.data.preferences

/**
 * 계정별 설정 스냅샷. Android/DataStore 구현에 의존하지 않아 병합 정책을 JVM에서 검증합니다.
 * 신체 수치의 null은 미입력을 뜻하며, 시간은 기기의 현지 시각(24시간제)입니다.
 */
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
