package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.model.PoseData
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/** Extracts angle vectors in the same order as the standard-pose asset. */
object PoseAngleExtractor {
    private const val MIN_VISIBILITY = 0.65f
    private const val MIN_VECTOR_MAGNITUDE = 0.0001f

    fun analyzePose(exerciseType: String, poseData: PoseData): Double {
        val landmarks = poseData.landmarks
        val points = when (exerciseType.uppercase()) {
            "SQUAT" -> intArrayOf(23, 25, 27)
            "SHOULDER_PRESS" -> intArrayOf(11, 13, 15)
            else -> return 0.0
        }

        if (points.any { index ->
                index >= landmarks.size || landmarks[index].visibility < MIN_VISIBILITY
            }) {
            return 0.0
        }

        return calculateAngle(
            landmarks[points[0]],
            landmarks[points[1]],
            landmarks[points[2]]
        )
    }

    fun extractSquatAngles(landmarks: List<NormalizedLandmark>): FloatArray? {
        if (landmarks.size < 29) return null

        val requiredIndices = intArrayOf(11, 12, 23, 24, 25, 26, 27, 28)
        if (requiredIndices.any { landmarks[it].visibility().orElse(0f) < MIN_VISIBILITY }) return null

        val leftKnee = calculate3DAngle(landmarks[23], landmarks[25], landmarks[27]) ?: return null
        val rightKnee = calculate3DAngle(landmarks[24], landmarks[26], landmarks[28]) ?: return null
        val leftHip = calculate3DAngle(landmarks[11], landmarks[23], landmarks[25]) ?: return null
        val rightHip = calculate3DAngle(landmarks[12], landmarks[24], landmarks[26]) ?: return null

        return floatArrayOf(leftKnee, rightKnee, leftHip, rightHip)
    }

    fun extractShoulderPressAngles(landmarks: List<NormalizedLandmark>): FloatArray? {
        if (landmarks.size < 17) return null

        val requiredIndices = intArrayOf(11, 12, 13, 14, 15, 16)
        if (requiredIndices.any { landmarks[it].visibility().orElse(0f) < MIN_VISIBILITY }) return null

        val leftElbow = calculate3DAngle(landmarks[11], landmarks[13], landmarks[15]) ?: return null
        val rightElbow = calculate3DAngle(landmarks[12], landmarks[14], landmarks[16]) ?: return null
        return floatArrayOf(leftElbow, rightElbow)
    }

    private fun calculate3DAngle(
        first: NormalizedLandmark,
        middle: NormalizedLandmark,
        last: NormalizedLandmark
    ): Float? {
        val firstVector = floatArrayOf(first.x() - middle.x(), first.y() - middle.y(), first.z() - middle.z())
        val lastVector = floatArrayOf(last.x() - middle.x(), last.y() - middle.y(), last.z() - middle.z())
        val firstMagnitude = sqrt(firstVector.sumOf { (it * it).toDouble() }).toFloat()
        val lastMagnitude = sqrt(lastVector.sumOf { (it * it).toDouble() }).toFloat()
        if (firstMagnitude < MIN_VECTOR_MAGNITUDE || lastMagnitude < MIN_VECTOR_MAGNITUDE) return null

        val dotProduct = firstVector.indices.sumOf { (firstVector[it] * lastVector[it]).toDouble() }.toFloat()
        val cosine = dotProduct / (firstMagnitude * lastMagnitude)
        return Math.toDegrees(acos(cosine.coerceIn(-1f, 1f).toDouble())).toFloat()
    }

    private fun calculateAngle(
        first: com.example.arptapp.data.model.Landmark,
        middle: com.example.arptapp.data.model.Landmark,
        last: com.example.arptapp.data.model.Landmark
    ): Double {
        val firstAngle = atan2(
            (first.y - middle.y).toDouble(),
            (first.x - middle.x).toDouble()
        )
        val lastAngle = atan2(
            (last.y - middle.y).toDouble(),
            (last.x - middle.x).toDouble()
        )
        val radians = lastAngle - firstAngle
        val angle = Math.abs(Math.toDegrees(radians))
        return if (angle > 180.0) 360.0 - angle else angle
    }
}
