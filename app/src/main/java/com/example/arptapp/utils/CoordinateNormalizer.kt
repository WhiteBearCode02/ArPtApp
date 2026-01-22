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
        // MediaPipe 랜드마크 인덱스 정의
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

        // [1단계] rawLandmarks(MediaPipe 객체)를 Landmark(우리 데이터 클래스)로 변환
        val landmarks = rawLandmarks.map {
            if (isMirrored) {
                // 전면 카메라 거울 효과 보정 (1 - x)
                Landmark(
                    x = 1f - it.x(),
                    y = it.y(),
                    z = it.z(),
                    visibility = it.visibility().orElse(0f)
                )
            } else {
                Landmark(
                    x = it.x(),
                    y = it.y(),
                    z = it.z(),
                    visibility = it.visibility().orElse(0f)
                )
            }
        }

        // [2단계] 골반 중심점 계산 (원점 설정)
        val leftHip = landmarks[LEFT_HIP]
        val rightHip = landmarks[RIGHT_HIP]
        val hipCenterX = (leftHip.x + rightHip.x) / 2
        val hipCenterY = (leftHip.y + rightHip.y) / 2
        val hipCenterZ = (leftHip.z + rightHip.z) / 2

        // [3단계] 신체 크기 스케일 계산 (어깨-골반 거리 기준)
        val leftShoulder = landmarks[LEFT_SHOULDER]
        // [수정] .z() 대신 .z 속성으로 접근하여 에러 해결
        val bodyScale = calculateDistance(
            leftShoulder.x, leftShoulder.y, leftShoulder.z,
            leftHip.x, leftHip.y, leftHip.z
        )

        // [4단계] 모든 좌표를 골반 중심(0,0,0) 기준으로 정규화
        val normalizedLandmarks = landmarks.map { landmark ->
            Landmark(
                x = (landmark.x - hipCenterX) / bodyScale,
                y = (landmark.y - hipCenterY) / bodyScale,
                z = (landmark.z - hipCenterZ) / bodyScale,
                visibility = landmark.visibility
            )
        }

        // [5단계] 정규화된 좌표 기반으로 각도 계산
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

    private fun calculateDistance(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1
        return sqrt(dx * dx + dy * dy + dz * dz)
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