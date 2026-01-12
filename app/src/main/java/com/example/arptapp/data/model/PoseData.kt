package com.example.arptapp.data.model

/**
 * 단일 프레임의 포즈 데이터
 */
data class PoseData(
    val timestamp: Long,
    val landmarks: List<Landmark>,
    val angles: Map<String, Float> // 관절별 각도
)

/**
 * MediaPipe 랜드마크 정보
 */
data class Landmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float
)

/**
 * 정규화된 포즈 데이터 (신체 비율 보정)
 */
data class NormalizedPoseData(
    val timestamp: Long,
    val normalizedLandmarks: List<Landmark>,
    val angles: Map<String, Float>,
    val hipCenterX: Float, // 골반 중심점 (원점)
    val hipCenterY: Float,
    val bodyScale: Float   // 신체 크기 스케일
)
