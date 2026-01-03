package com.example.arptapp.data.model

/**
 * AI-Hub에서 추출한 표준 자세 템플릿
 */
data class StandardPose(
    val exerciseType: String,        // "SQUAT", "LUNGE", etc.
    val sequence: List<PoseData>,    // 1회 반복의 프레임별 데이터
    val keyAngles: Map<String, AngleRange>, // 허용 각도 범위
    val duration: Long,              // 표준 동작 시간 (ms)
    val description: String          // 운동 설명
)

/**
 * 각도 허용 범위
 */
data class AngleRange(
    val min: Float,
    val max: Float,
    val optimal: Float
)
