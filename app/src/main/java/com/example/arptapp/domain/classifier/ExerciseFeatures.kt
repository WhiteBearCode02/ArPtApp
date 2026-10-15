package com.example.arptapp.domain.classifier

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.JointAngleMeasurement
import com.example.arptapp.domain.analyzer.CoordinateSpace

/** Scalar fallback input, never a server DTO. Null is unavailable, not a straight/normal joint. */
data class ExerciseSideFeatures(
    val knee: Double? = null, val hip: Double? = null, val elbow: Double? = null,
    val wristAboveShoulder: Boolean? = null, val legVisibility: Float = 0f, val armVisibility: Float = 0f
)
data class ExerciseFeatures(val left: ExerciseSideFeatures, val right: ExerciseSideFeatures)

object ExerciseFeatureExtractor {
    fun extract(points: List<Landmark>, view: CameraView, aspect: Double = 1.0): ExerciseFeatures {
        fun side(offset: Int): ExerciseSideFeatures {
            fun visibility(vararg ids: Int): Float = ids.minOf { id ->
                points.getOrNull(id + offset)?.takeIf { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }
                    ?.visibility?.takeIf { it.isFinite() && it in 0f..1f } ?: 0f
            }
            fun angle(vararg ids: Int) = JointAngleMeasurement.measure(points, ids.map { it + offset },
                "fallback", view = view, imageAspectRatio = aspect,
                // Retain normalized-depth evidence for FRONT; SIDE uses the visible sagittal projection.
                // Neither is a calibrated world/clinical joint angle.
                space = if (view == CameraView.FRONT) CoordinateSpace.NORMALIZED_LANDMARK else CoordinateSpace.IMAGE_2D).valueDeg
            val leg = visibility(11, 23, 25, 27)
            val arm = visibility(11, 13, 15)
            return ExerciseSideFeatures(angle(23, 25, 27), angle(11, 23, 25), angle(11, 13, 15),
                if (arm >= 0.65f) points[15 + offset].y < points[11 + offset].y - 0.015f else null, leg, arm)
        }
        return ExerciseFeatures(side(0), side(1))
    }
}
