package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class LungeAnalyzer : BaseExerciseAnalyzer {
    private companion object {
        const val MIN_VISIBILITY = 0.65f
    }

    private val repCounter = LungeRepCounter()
    private var lastFormStatus = false

    override fun analyze(landmarks: List<NormalizedLandmark>): Int {
        val angles = PoseAngleExtractor.extractSquatAngles(landmarks)
        if (angles == null || !hasVisibleLegs(landmarks)) {
            lastFormStatus = false
            return repCounter.count
        }

        val leftKnee = angles[0].toDouble()
        val rightKnee = angles[1].toDouble()
        val frontKnee = minOf(leftKnee, rightKnee)
        val backKnee = maxOf(leftKnee, rightKnee)
        lastFormStatus = true
        return repCounter.update(frontKnee, backKnee, System.currentTimeMillis())
    }

    override fun reset() {
        repCounter.reset()
        lastFormStatus = false
    }

    override fun isProperForm(): Boolean = lastFormStatus

    private fun hasVisibleLegs(landmarks: List<NormalizedLandmark>): Boolean {
        val indices = intArrayOf(23, 24, 25, 26, 27, 28)
        return landmarks.size >= 29 && indices.all { landmarks[it].visibility().orElse(0f) >= MIN_VISIBILITY }
    }
}
