package com.example.arptapp.utils

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.data.model.NormalizedPoseData
import com.example.arptapp.data.model.PoseData
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.sqrt

/**
 * 기획서 3.2.2절: 신체 비율 및 좌표 정규화
 * 사용자의 체형과 카메라 거리에 무관하게 자세를 비교하기 위한 정규화
 */
class CoordinateNormalizer {
    
    companion object {
        // MediaPipe 랜드마크 인덱스
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
    }
    
    /**
     * MediaPipe 결과를 정규화된 포즈 데이터로 변환
     */
    fun normalize(result: PoseLandmarkerResult): NormalizedPoseData? {
        if (result.landmarks().isEmpty()) return null
        
        val landmarks = result.landmarks()[0]
        
        // 1. 골반 중심점 계산 (원점으로 사용)
        val leftHip = landmarks[LEFT_HIP]
        val rightHip = landmarks[RIGHT_HIP]
        val hipCenterX = (leftHip.x() + rightHip.x()) / 2
        val hipCenterY = (leftHip.y() + rightHip.y()) / 2
        val hipCenterZ = (leftHip.z() + rightHip.z()) / 2
        
        // 2. 신체 크기 스케일 계산 (어깨-골반 거리 기준)
        val leftShoulder = landmarks[LEFT_SHOULDER]
        val bodyScale = calculateDistance(
            leftShoulder.x(), leftShoulder.y(), leftShoulder.z(),
            leftHip.x(), leftHip.y(), leftHip.z()
        )
        
        // 3. 모든 랜드마크를 골반 중심 기준으로 정규화
        val normalizedLandmarks = landmarks.map { landmark ->
            Landmark(
                x = (landmark.x() - hipCenterX) / bodyScale,
                y = (landmark.y() - hipCenterY) / bodyScale,
                z = (landmark.z() - hipCenterZ) / bodyScale,
                visibility = 0f)
        }
        
        // 4. 주요 관절 각도 계산
        val angles = calculateAllAngles(normalizedLandmarks)
        
        return NormalizedPoseData(
            timestamp = System.currentTimeMillis(),
            normalizedLandmarks = normalizedLandmarks,
            angles = angles,
            hipCenterX = hipCenterX,
            hipCenterY = hipCenterY,
            bodyScale = bodyScale
        )
    }
    
    /**
     * 3D 유클리드 거리 계산
     */
    private fun calculateDistance(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
    
    /**
     * 모든 주요 관절 각도 계산
     */
    private fun calculateAllAngles(landmarks: List<Landmark>): Map<String, Float> {
        return mapOf(
            "LEFT_KNEE" to calculateAngle(
                landmarks[LEFT_HIP],
                landmarks[25], // LEFT_KNEE
                landmarks[LEFT_ANKLE]
            ),
            "RIGHT_KNEE" to calculateAngle(
                landmarks[RIGHT_HIP],
                landmarks[26], // RIGHT_KNEE
                landmarks[RIGHT_ANKLE]
            ),
            "LEFT_HIP" to calculateAngle(
                landmarks[LEFT_SHOULDER],
                landmarks[LEFT_HIP],
                landmarks[25] // LEFT_KNEE
            ),
            "RIGHT_HIP" to calculateAngle(
                landmarks[RIGHT_SHOULDER],
                landmarks[RIGHT_HIP],
                landmarks[26] // RIGHT_KNEE
            )
            // 필요한 각도 추가...
        )
    }
    
    /**
     * 세 점으로 각도 계산 (기존 AngleCalculator와 유사)
     */
    private fun calculateAngle(
        firstPoint: Landmark,
        midPoint: Landmark,
        lastPoint: Landmark
    ): Float {
        val radians = kotlin.math.atan2(
            lastPoint.y - midPoint.y,
            lastPoint.x - midPoint.x
        ) - kotlin.math.atan2(
            firstPoint.y - midPoint.y,
            firstPoint.x - midPoint.x
        )
        
        var angle = Math.toDegrees(radians.toDouble()).toFloat()
        if (angle < 0) angle += 360f
        if (angle > 180) angle = 360f - angle
        
        return angle
    }
}
