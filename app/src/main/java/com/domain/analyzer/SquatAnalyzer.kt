package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

class SquatAnalyzer : BaseExerciseAnalyzer {
    private var squatCount = 0
    private var isDown = false
    private var lastFormStatus = true

    // === 스쿼트 분석 핵심 로직 ===
    override fun analyze(landmarks: List<NormalizedLandmark>): Int {
        // [1] 필요한 관절점 추출 (어깨, 골반, 무릎, 발목)
        val shoulderY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val hipY = (landmarks[23].y() + landmarks[24].y()) / 2f
        val kneeAngle = calculateAngle(landmarks[23], landmarks[25], landmarks[27])
        val ankleY = (landmarks[27].y() + landmarks[28].y()) / 2f

        // [2] 골반 하강 비율 계산 (창업자님의 추론 로직 반영) [cite: 2026-01-27]
        // 몸통 길이(torso) 대비 골반~발목 거리의 비율을 구합니다.
        val torsoHeight = abs(hipY - shoulderY)
        val hipToFloorDist = abs(ankleY - hipY)
        val descentRatio = hipToFloorDist / torsoHeight

        // [3] 상태 머신 판단
        // 무릎이 100도 이하로 굽혀지고, 골반이 몸통 길이만큼 충분히 내려갔을 때 '성공적인 하강'으로 인지
        if (kneeAngle < 100.0 && descentRatio < 1.0) {
            isDown = true
            lastFormStatus = true
        }
        // 다시 일어서서 무릎이 펴지고(160도) 골반 높이가 회복되었을 때 카운트 증가
        else if (isDown && kneeAngle > 160.0 && descentRatio > 1.3) {
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

    // === 3점 사이의 각도 계산 함수 ===
    private fun calculateAngle(
        first: NormalizedLandmark,
        mid: NormalizedLandmark,
        last: NormalizedLandmark
    ): Double {
        val radians = atan2((last.y() - mid.y()).toDouble(), (last.x() - mid.x()).toDouble()) -
                atan2((first.y() - mid.y()).toDouble(), (first.x() - mid.x()).toDouble())
        var angle = abs(radians * 180.0 / Math.PI)
        if (angle > 180.0) angle = 360.0 - angle
        return angle
    }
}