package com.example.arptapp.utils

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.data.model.NormalizedPoseData
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.sqrt

/**
 * 기획서 3.2.2절: 신체 비율 및 좌표 정규화
 * 사용자의 체형과 카메라 거리에 무관하게 자세를 비교하기 위한 정규화 클래스입니다.
 */
class CoordinateNormalizer {

    companion object {
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
    }

    /**
     * MediaPipe 결과를 정규화된 포즈 데이터로 변환합니다.
     * isMirrored: 전면 카메라 사용 시 좌우 반전 여부
     */
    fun normalize(result: PoseLandmarkerResult, isMirrored: Boolean = true): NormalizedPoseData? {
        if (result.landmarks().isEmpty()) return null

        val rawLandmarks = result.landmarks()[0]

        // [1단계] rawLandmarks를 우리 프로젝트의 Landmark 데이터 클래스로 매핑 및 전면 카메라 반전 적용
        val landmarks = rawLandmarks.map {
            if (isMirrored) {
                Landmark(1f - it.x(), it.y(), it.z(), it.visibility().orElse(0f))
            } else {
                Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f))
            }
        }

        // [2단계] 골반 중심점 계산 (정규화의 원점)
        val leftHip = landmarks[LEFT_HIP]
        val rightHip = landmarks[RIGHT_HIP]
        val hipCenterX = (leftHip.x + rightHip.x) / 2
        val hipCenterY = (leftHip.y + rightHip.y) / 2
        val hipCenterZ = (leftHip.z + rightHip.z) / 2

        // [3단계] 신체 크기 스케일 계산 (어깨-골반 거리를 1로 기준 잡기 위함)
        val leftShoulder = landmarks[LEFT_SHOULDER]
        val bodyScale = calculateDistance(
            leftShoulder.x, leftShoulder.y, leftShoulder.z,
            leftHip.x, leftHip.y, leftHip.z
        )

        // [4단계] 모든 랜드마크를 골반 중심 기준으로 이동 및 스케일 조정
        val normalizedLandmarks = landmarks.map { landmark ->
            Landmark(
                x = (landmark.x - hipCenterX) / bodyScale,
                y = (landmark.y - hipCenterY) / bodyScale,
                z = (landmark.z - hipCenterZ) / bodyScale,
                visibility = landmark.visibility
            )
        }

        // [5단계] 정규화된 좌표를 바탕으로 관절 각도 계산
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
     * [수정] 3D 유클리드 거리 계산 함수
     * 코틀린 문법에 맞게 변수를 먼저 선언한 후 계산하도록 변경했습니다.
     */
    private fun calculateDistance(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1

        // 3D 거리 공식: sqrt(dx^2 + dy^2 + dz^2)
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }



    private fun calculateAllAngles(landmarks: List<Landmark>): Map<String, Float> {
        return mapOf(
            "LEFT_KNEE" to calculateAngle(landmarks[LEFT_HIP], landmarks[25], landmarks[LEFT_ANKLE]),
            "RIGHT_KNEE" to calculateAngle(landmarks[RIGHT_HIP], landmarks[26], landmarks[RIGHT_ANKLE]),
            "LEFT_HIP" to calculateAngle(landmarks[LEFT_SHOULDER], landmarks[LEFT_HIP], landmarks[25]),
            "RIGHT_HIP" to calculateAngle(landmarks[RIGHT_SHOULDER], landmarks[RIGHT_HIP], landmarks[26])
        )
    }

    private fun calculateAngle(first: Landmark, mid: Landmark, last: Landmark): Float {
        val radians = kotlin.math.atan2(last.y - mid.y, last.x - mid.x) -
                kotlin.math.atan2(first.y - mid.y, first.x - mid.x)

        var angle = Math.toDegrees(radians.toDouble()).toFloat()
        if (angle < 0) angle += 360f
        if (angle > 180) angle = 360f - angle

        return angle
    }
}