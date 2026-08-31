package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.sqrt

class SquatAnalyzer : BaseExerciseAnalyzer {
    private companion object {
        const val MIN_LANDMARK_VISIBILITY = 0.65f
        const val SMOOTHING_WINDOW_SIZE = 5
        const val MIN_VECTOR_MAGNITUDE = 0.0001f
        const val MIN_TORSO_HEIGHT = 0.0001f
    }

    private data class LegLandmarks(
        val shoulder: NormalizedLandmark,
        val hip: NormalizedLandmark,
        val knee: NormalizedLandmark,
        val ankle: NormalizedLandmark
    )

    private val repCounter = SquatRepCounter()
    private val kneeAngleWindow = ArrayDeque<Double>()
    private val descentRatioWindow = ArrayDeque<Double>()
    private var lastFormStatus = false

    override fun analyze(landmarks: List<NormalizedLandmark>): Int {
        val leg = selectMostVisibleLeg(landmarks)
        if (leg == null) {
            lastFormStatus = false
            return repCounter.count
        }

        val kneeAngle = calculate3DAngle(leg.hip, leg.knee, leg.ankle)
        val torsoHeight = abs(leg.hip.y() - leg.shoulder.y())
        if (kneeAngle == null || torsoHeight < MIN_TORSO_HEIGHT) {
            lastFormStatus = false
            return repCounter.count
        }

        val hipToFloorDist = abs(leg.ankle.y() - leg.hip.y())
        val descentRatio = hipToFloorDist / torsoHeight

        val smoothedKneeAngle = smooth(kneeAngleWindow, kneeAngle)
        val smoothedDescentRatio = smooth(descentRatioWindow, descentRatio.toDouble())
        lastFormStatus = true
        return repCounter.update(smoothedKneeAngle, smoothedDescentRatio, System.currentTimeMillis())
    }

    override fun reset() {
        repCounter.reset()
        kneeAngleWindow.clear()
        descentRatioWindow.clear()
        lastFormStatus = false
    }

    override fun isProperForm(): Boolean = lastFormStatus

    private fun selectMostVisibleLeg(landmarks: List<NormalizedLandmark>): LegLandmarks? {
        if (landmarks.size < 29) return null

        val left = LegLandmarks(landmarks[11], landmarks[23], landmarks[25], landmarks[27])
        val right = LegLandmarks(landmarks[12], landmarks[24], landmarks[26], landmarks[28])
        val selected = listOf(left, right).maxByOrNull(::minimumVisibility) ?: return null

        return selected.takeIf { minimumVisibility(it) >= MIN_LANDMARK_VISIBILITY }
    }

    private fun minimumVisibility(leg: LegLandmarks): Float = listOf(
        leg.shoulder.visibility().orElse(0f),
        leg.hip.visibility().orElse(0f),
        leg.knee.visibility().orElse(0f),
        leg.ankle.visibility().orElse(0f)
    ).minOrNull() ?: 0f

    private fun smooth(window: ArrayDeque<Double>, value: Double): Double {
        window.addLast(value)
        if (window.size > SMOOTHING_WINDOW_SIZE) window.removeFirst()
        return window.sorted()[window.size / 2]
    }

    private fun calculate3DAngle(
        p1: NormalizedLandmark,
        p2: NormalizedLandmark,
        p3: NormalizedLandmark
    ): Double? {
        val v1x = p1.x() - p2.x()
        val v1y = p1.y() - p2.y()
        val v1z = p1.z() - p2.z()

        val v2x = p3.x() - p2.x()
        val v2y = p3.y() - p2.y()
        val v2z = p3.z() - p2.z()

        val dotProduct = v1x * v2x + v1y * v2y + v1z * v2z
        val mag1 = sqrt(v1x * v1x + v1y * v1y + v1z * v1z)
        val mag2 = sqrt(v2x * v2x + v2y * v2y + v2z * v2z)
        if (mag1 < MIN_VECTOR_MAGNITUDE || mag2 < MIN_VECTOR_MAGNITUDE) return null

        val cosTheta = dotProduct / (mag1 * mag2)
        return Math.toDegrees(acos(cosTheta.coerceIn(-1f, 1f).toDouble()))
    }
}
