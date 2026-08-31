package com.example.arptapp.data.model

/**
 * AI-Hub에서 추출한 표준 자세 템플릿
 */
data class StandardPose(
    val exerciseType: String,        // "SQUAT", "LUNGE", etc.
    /**
     * One repetition represented as ordered angle vectors.
     * The current asset order is LEFT_KNEE, RIGHT_KNEE, LEFT_HIP, RIGHT_HIP.
     */
    val sequence: List<List<Float>>,
    val keyAngles: Map<String, AngleRange>, // 허용 각도 범위
    val duration: Long,              // 표준 동작 시간 (ms)
    val description: String          // 운동 설명
)

fun StandardPose.toAngleSequence(): List<FloatArray> = sequence.map { it.toFloatArray() }

/**
 * 각도 허용 범위
 */
data class AngleRange(
    val min: Float,
    val max: Float,
    val optimal: Float
)
