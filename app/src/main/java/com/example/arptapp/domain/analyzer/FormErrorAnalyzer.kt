package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2

object FormErrorAnalyzer {
    data class Assessment(val evaluable: Boolean, val errors: List<String>, val reason: String? = null)
    const val ERROR_KNEE_VALGUS = "ERROR_KNEE_VALGUS"
    const val ERROR_FORWARD_LEAN = "ERROR_FORWARD_LEAN"

    private const val MIN_VISIBILITY = 0.65f
    private const val VALGUS_RATIO_THRESHOLD = 0.08f
    private const val FORWARD_LEAN_DEGREES = 20.0

    fun assessSquat(landmarks: List<NormalizedLandmark>, view: CameraView): Assessment {
        if (view == CameraView.UNKNOWN) return Assessment(false, emptyList(), "촬영 방향을 선택하면 자세 코칭을 확인할 수 있습니다")
        val required = if (view == CameraView.FRONT) intArrayOf(23, 24, 25, 26, 27, 28) else intArrayOf(11, 12, 23, 24)
        if (required.any { it >= landmarks.size || landmarks[it].visibility().orElse(0f) < MIN_VISIBILITY ||
                !landmarks[it].x().isFinite() || !landmarks[it].y().isFinite() }) {
            return Assessment(false, emptyList(), "코칭 측정 대기 · 필요한 관절이 가려져 있습니다")
        }
        val errors = when (view) {
            CameraView.FRONT -> if (hasKneeValgus(landmarks, 23, 25, 27) || hasKneeValgus(landmarks, 24, 26, 28)) listOf(ERROR_KNEE_VALGUS) else emptyList()
            CameraView.SIDE -> if (hasForwardLean(landmarks[11], landmarks[23]) || hasForwardLean(landmarks[12], landmarks[24])) listOf(ERROR_FORWARD_LEAN) else emptyList()
            else -> emptyList()
        }
        return Assessment(true, errors)
    }

    fun analyzeSquat(landmarks: List<NormalizedLandmark>): List<String> {
        if (landmarks.size < 29) return emptyList()
        val required = intArrayOf(11, 12, 23, 24, 25, 26, 27, 28)
        if (required.any { landmarks[it].visibility().orElse(0f) < MIN_VISIBILITY }) {
            return emptyList()
        }

        val errors = mutableListOf<String>()
        if (hasKneeValgus(landmarks, 23, 25, 27) || hasKneeValgus(landmarks, 24, 26, 28)) {
            errors += ERROR_KNEE_VALGUS
        }
        if (hasForwardLean(landmarks[11], landmarks[23]) || hasForwardLean(landmarks[12], landmarks[24])) {
            errors += ERROR_FORWARD_LEAN
        }
        return errors
    }

    private fun hasKneeValgus(
        landmarks: List<NormalizedLandmark>,
        hipIndex: Int,
        kneeIndex: Int,
        ankleIndex: Int
    ): Boolean {
        val hip = landmarks[hipIndex]
        val knee = landmarks[kneeIndex]
        val ankle = landmarks[ankleIndex]
        val centerX = (hip.x() + ankle.x()) / 2f
        val legWidth = abs(hip.x() - ankle.x()).coerceAtLeast(0.1f)
        val inwardOffset = if (hip.x() < ankle.x()) knee.x() - centerX else centerX - knee.x()
        return inwardOffset / legWidth > VALGUS_RATIO_THRESHOLD
    }

    private fun hasForwardLean(shoulder: NormalizedLandmark, hip: NormalizedLandmark): Boolean {
        val dx = abs(shoulder.x() - hip.x()).toDouble()
        val dy = abs(shoulder.y() - hip.y()).toDouble()
        if (dx == 0.0 && dy == 0.0) return false
        val angleFromVertical = Math.toDegrees(atan2(dx, dy))
        return angleFromVertical > FORWARD_LEAN_DEGREES
    }
}
