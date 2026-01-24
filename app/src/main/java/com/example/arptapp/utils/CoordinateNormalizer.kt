package com.example.arptapp.utils

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.data.model.NormalizedPoseData
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.sqrt

/**
 * 좌표 정규화 유틸리티
 *
 * 사용자의 체형과 카메라 거리에 무관하게 포즈를 비교하기 위해
 * MediaPipe로부터 받은 랜드마크 좌표를 정규화합니다.
 *
 * [정규화 프로세스]
 * 1. 골반 중심을 원점(0,0,0)으로 설정
 * 2. 어깨-골반 거리를 기준으로 스케일 조정
 * 3. 전면 카메라 사용 시 좌우 미러링 처리
 * 4. 관절 각도 계산
 */
class CoordinateNormalizer {

    companion object {
        // MediaPipe Pose Landmarker 인덱스 상수
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ANKLE = 27
        const val RIGHT_ANKLE = 28
        const val LEFT_KNEE = 25
        const val RIGHT_KNEE = 26
    }

    /**
     * MediaPipe 결과를 정규화된 포즈 데이터로 변환
     *
     * @param result MediaPipe PoseLandmarker 추론 결과
     * @param isMirrored 전면 카메라 사용 여부 (true: 좌우 반전, false: 원본)
     * @return 정규화된 포즈 데이터 또는 null (랜드마크가 감지되지 않은 경우)
     */
    fun normalize(result: PoseLandmarkerResult, isMirrored: Boolean = true): NormalizedPoseData? {
        // 랜드마크가 감지되지 않은 경우 null 반환
        if (result.landmarks().isEmpty()) return null

        val rawLandmarks = result.landmarks()[0]

        // [1단계] MediaPipe 랜드마크를 프로젝트 모델로 변환 및 미러링 처리
        val landmarks = rawLandmarks.map {
            if (isMirrored) {
                // 전면 카메라: X좌표 반전 (1 - x)
                Landmark(1f - it.x(), it.y(), it.z(), it.visibility().orElse(0f))
            } else {
                // 후면 카메라: 원본 좌표 사용
                Landmark(it.x(), it.y(), it.z(), it.visibility().orElse(0f))
            }
        }

        // [2단계] 골반 중심점 계산 (정규화의 기준점)
        val leftHip = landmarks[LEFT_HIP]
        val rightHip = landmarks[RIGHT_HIP]
        val hipCenterX = (leftHip.x + rightHip.x) / 2
        val hipCenterY = (leftHip.y + rightHip.y) / 2
        val hipCenterZ = (leftHip.z + rightHip.z) / 2

        // [3단계] 신체 스케일 계산 (어깨-골반 거리를 기준으로 정규화)
        // 키가 크거나 카메라와 가까이 있어도 동일한 비율로 변환됨
        val leftShoulder = landmarks[LEFT_SHOULDER]
        val bodyScale = calculateDistance(
            leftShoulder.x, leftShoulder.y, leftShoulder.z,
            leftHip.x, leftHip.y, leftHip.z
        )

        // [4단계] 모든 랜드마크를 골반 중심 기준으로 이동 및 스케일 정규화
        val normalizedLandmarks = landmarks.map { landmark ->
            Landmark(
                x = (landmark.x - hipCenterX) / bodyScale,
                y = (landmark.y - hipCenterY) / bodyScale,
                z = (landmark.z - hipCenterZ) / bodyScale,
                visibility = landmark.visibility
            )
        }

        // [5단계] 정규화된 좌표 기반 관절 각도 계산
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
     *
     * @return 두 점 사이의 직선 거리
     */
    private fun calculateDistance(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1
        return sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
    }

    /**
     * 주요 관절 각도를 일괄 계산
     *
     * @return 관절명-각도 맵 (무릎, 고관절)
     */
    private fun calculateAllAngles(landmarks: List<Landmark>): Map<String, Float> {
        return mapOf(
            "LEFT_KNEE" to calculateAngle(
                landmarks[LEFT_HIP],
                landmarks[LEFT_KNEE],
                landmarks[LEFT_ANKLE]
            ),
            "RIGHT_KNEE" to calculateAngle(
                landmarks[RIGHT_HIP],
                landmarks[RIGHT_KNEE],
                landmarks[RIGHT_ANKLE]
            ),
            "LEFT_HIP" to calculateAngle(
                landmarks[LEFT_SHOULDER],
                landmarks[LEFT_HIP],
                landmarks[LEFT_KNEE]
            ),
            "RIGHT_HIP" to calculateAngle(
                landmarks[RIGHT_SHOULDER],
                landmarks[RIGHT_HIP],
                landmarks[RIGHT_KNEE]
            )
        )
    }

    /**
     * 3점을 이용한 관절 각도 계산
     *
     * @param first 첫 번째 점 (예: 어깨)
     * @param mid 중간 점 (관절, 예: 골반)
     * @param last 마지막 점 (예: 무릎)
     * @return 관절 각도 (0~180도)
     */
    private fun calculateAngle(first: Landmark, mid: Landmark, last: Landmark): Float {
        // atan2를 이용한 벡터 각도 계산
        val radians = kotlin.math.atan2(last.y - mid.y, last.x - mid.x) -
                kotlin.math.atan2(first.y - mid.y, first.x - mid.x)

        // 라디안을 도(degree)로 변환
        var angle = Math.toDegrees(radians.toDouble()).toFloat()

        // 음수 각도를 양수로 변환
        if (angle < 0) angle += 360f

        // 180도 이상은 반대편 각도로 변환 (예: 270도 → 90도)
        if (angle > 180) angle = 360f - angle

        return angle
    }
}