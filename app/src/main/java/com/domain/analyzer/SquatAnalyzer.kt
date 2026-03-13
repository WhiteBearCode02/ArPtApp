package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.*

class SquatAnalyzer : BaseExerciseAnalyzer {
    private var squatCount = 0
    private var isDown = false
    private var lastFormStatus = true

    // === 스쿼트 분석 핵심 로직 ===
    override fun analyze(landmarks: List<NormalizedLandmark>): Int {
        // [1] 가시성(Visibility) 점수를 비교하여 더 잘 보이는 쪽의 다리를 선택합니다.
        // 왼쪽 무릎(26)과 오른쪽 무릎(25) 중 카메라에 더 명확하게 노출된 쪽을 찾습니다.
        val leftKneeVisible = landmarks[26].visibility().orElse(0f)
        val rightKneeVisible = landmarks[25].visibility().orElse(0f)

        val (hip, knee, ankle) = if (leftKneeVisible > rightKneeVisible) {
            // 왼쪽 다리가 더 잘 보일 때 (측면/정면 대응)
            Triple(landmarks[24], landmarks[26], landmarks[28])
        } else {
            // 오른쪽 다리가 더 잘 보일 때
            Triple(landmarks[23], landmarks[25], landmarks[27])
        }

        // [2] 3D 벡터 내적을 이용한 정밀 각도 계산 (z축 반영)
        val kneeAngle = calculate3DAngle(hip, knee, ankle)

        // [3] 골반 하강 비율 계산 (기준 관절도 가시성에 따라 선택)
        val shoulderY = if (leftKneeVisible > rightKneeVisible) landmarks[12].y() else landmarks[11].y()
        val torsoHeight = abs(hip.y() - shoulderY)
        val hipToFloorDist = abs(ankle.y() - hip.y())
        val descentRatio = hipToFloorDist / torsoHeight

        // [4] 상태 머신 판단 (임계값 최적화)
        // 측면 인식률을 위해 하강 판정 각도를 100도에서 105도로 약간 완화했습니다.
        if (kneeAngle < 105.0 && descentRatio < 1.0) {
            isDown = true
            lastFormStatus = true
        }
        // 다시 일어서서 무릎이 펴질 때 카운트 증가
        else if (isDown && kneeAngle > 160.0 && descentRatio > 1.2) {
            squatCount++
            isDown = false
        }

        return squatCount
    }

    override fun reset() {
        squatCount = 0
        isDown = false
    }

    override fun isProperForm(): Boolean = lastFormStatus

    /**
     * [3D 벡터 내적 공식]
     * x, y 좌표뿐만 아니라 z축(깊이)을 포함하여 실제 공간상의 각도를 계산합니다.
     * 측면에서 보았을 때 다리가 앞뒤로 움직이는 궤적을 정확히 포착할 수 있습니다.
     */
    private fun calculate3DAngle(
        p1: NormalizedLandmark,
        p2: NormalizedLandmark,
        p3: NormalizedLandmark
    ): Double {
        // p2(무릎)를 원점으로 하는 두 벡터 생성
        val v1x = p1.x() - p2.x()
        val v1y = p1.y() - p2.y()
        val v1z = p1.z() - p2.z()

        val v2x = p3.x() - p2.x()
        val v2y = p3.y() - p2.y()
        val v2z = p3.z() - p2.z()

        // 벡터 내적 계산: a·b = |a||b|cos(θ)
        val dotProduct = v1x * v2x + v1y * v2y + v1z * v2z
        val mag1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val mag2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)

        // cos 역함수(acos)를 통해 사잇각 도출
        val cosTheta = dotProduct / (mag1 * mag2)
        // 수치 오류 방지를 위해 -1~1 사이로 값 고정
        val clampedCos = max(-1.0, min(1.0, cosTheta.toDouble()))
        
        return Math.toDegrees(acos(clampedCos))
    }
}