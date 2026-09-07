package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class ShoulderPressAnalyzer : BaseExerciseAnalyzer {
    private var isDown = false
    private var count = 0
    private var lastFormStatus = false

    override fun analyze(landmarks: List<NormalizedLandmark>): Int {
        val angles = PoseAngleExtractor.extractShoulderPressAngles(landmarks)
        if (angles == null) {
            lastFormStatus = false
            return count
        }

        val elbowAngle = angles.average()
        lastFormStatus = true
        if (!isDown && elbowAngle <= DOWN_ANGLE) {
            isDown = true
        } else if (isDown && elbowAngle >= UP_ANGLE) {
            count++
            isDown = false
        }
        return count
    }

    override fun reset() {
        isDown = false
        count = 0
        lastFormStatus = false
    }

    override fun isProperForm(): Boolean = lastFormStatus

    private companion object {
        const val DOWN_ANGLE = 100.0
        const val UP_ANGLE = 155.0
    }
}
