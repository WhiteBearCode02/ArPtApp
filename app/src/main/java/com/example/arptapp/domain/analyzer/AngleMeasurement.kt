package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.model.Landmark
import kotlin.math.acos
import kotlin.math.sqrt

enum class CameraView { FRONT, SIDE, UNKNOWN }
enum class MeasurementQuality { VALID, LOW_VISIBILITY, MISSING, DEGENERATE_VECTOR, NON_FINITE }
enum class CoordinateSpace { IMAGE_2D, NORMALIZED_LANDMARK }

/** Interior angles, never clinical flexion angles or motion-capture world angles. */
data class AngleMeasurement(
    val metricId: String,
    val valueDeg: Double?,
    val coordinateSpace: CoordinateSpace,
    val cameraView: CameraView,
    val quality: MeasurementQuality,
    val minVisibility: Float,
    val angleDefinition: String = "INTERIOR",
    val sourceType: String = "ENGINEERING_INITIAL"
) {
    val isEvaluable: Boolean get() = quality == MeasurementQuality.VALID && valueDeg != null
    val flexionDeg: Double? get() = valueDeg?.let { 180.0 - it }
}

/** Shared measurement API for camera frames and offline unit tests. */
object JointAngleMeasurement {
    fun measure(
        points: List<Landmark>, indices: List<Int>, metricId: String,
        space: CoordinateSpace = CoordinateSpace.IMAGE_2D,
        view: CameraView = CameraView.UNKNOWN,
        imageAspectRatio: Double = 1.0
    ): AngleMeasurement {
        fun unavailable(reason: MeasurementQuality, visibility: Float = 0f) =
            AngleMeasurement(metricId, null, space, view, reason, visibility)
        if (indices.size != 3 || indices.any { it !in points.indices }) return unavailable(MeasurementQuality.MISSING)
        val p = indices.map(points::get)
        if (!imageAspectRatio.isFinite() || imageAspectRatio <= 0 || p.any {
                !it.x.isFinite() || !it.y.isFinite() || !it.z.isFinite() || !it.visibility.isFinite()
            }) return unavailable(MeasurementQuality.NON_FINITE)
        val visibility = p.minOf { it.visibility }
        if (visibility < 0.65f) return unavailable(MeasurementQuality.LOW_VISIBILITY, visibility)
        fun vector(a: Landmark, b: Landmark) = doubleArrayOf(
            (a.x - b.x) * imageAspectRatio, (a.y - b.y).toDouble(),
            if (space == CoordinateSpace.NORMALIZED_LANDMARK) (a.z - b.z).toDouble() else 0.0
        )
        val a = vector(p[0], p[1])
        val b = vector(p[2], p[1])
        val magnitude = sqrt(a.sumOf { it * it }) * sqrt(b.sumOf { it * it })
        if (magnitude < 1e-8) return unavailable(MeasurementQuality.DEGENERATE_VECTOR, visibility)
        val angle = Math.toDegrees(acos((a.indices.sumOf { a[it] * b[it] } / magnitude).coerceIn(-1.0, 1.0)))
        return AngleMeasurement(metricId, angle, space, view, MeasurementQuality.VALID, visibility)
    }
}
