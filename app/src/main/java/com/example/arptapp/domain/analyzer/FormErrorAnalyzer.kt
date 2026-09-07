package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.toDegrees

object FormErrorAnalyzer {
    const val ERROR_KNEE_VALGUS = "ERROR_KNEE_VALGUS"
    const val ERROR_FORWARD_LEAN = "ERROR_FORWARD_LEAN"

    private const val MIN_VISIBILITY = 0.65f
    private const val VALGUS_RATIO_THRESHOLD = 0.08f
    private const val FORWARD_LEAN_DEGREES = 20.0

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
