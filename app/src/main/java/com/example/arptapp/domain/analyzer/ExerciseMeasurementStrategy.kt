package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.model.Landmark
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

/** Transient, view-qualified scalar port; coordinates never leave the camera adapter. */
data class ExerciseMeasurement(
    val representative: AngleMeasurement, val leftElbow: Double? = null, val rightElbow: Double? = null,
    val upperArmLeft: Double? = null, val upperArmRight: Double? = null,
    val torsoInclination: Double? = null, val bodyLineDeviation: Double? = null,
    val lateralFlexion: Double? = null, val shoulderHeightDifference: Double? = null
)
fun interface ExerciseMeasurementStrategy {
    fun measure(points: List<Landmark>, view: CameraView, aspect: Double): ExerciseMeasurement
}

/** Keep legacy squat/press angle APIs untouched; new exercises share only validated geometry. */
object ExerciseMeasurements {
    private val strategies: Map<String, ExerciseMeasurementStrategy> = mapOf(
        "BICEP_CURL" to BicepCurlMeasurementStrategy(), "PUSH_UP" to PushUpMeasurementStrategy(),
        "BENT_OVER_ROW" to BentOverRowMeasurementStrategy(), "TRICEPS_EXTENSION" to TricepsExtensionMeasurementStrategy(),
        "SIDE_BEND" to SideBendMeasurementStrategy())
    fun measure(type: String, points: List<Landmark>, view: CameraView, aspect: Double = 1.0): ExerciseMeasurement? =
        strategies[type]?.measure(points, view, aspect)

    internal fun unavailable(view: CameraView) = AngleMeasurement("unavailable", null, CoordinateSpace.IMAGE_2D,
        view, MeasurementQuality.MISSING, 0f)
    internal fun valid(points: List<Landmark>, aspect: Double, vararg ids: Int): Boolean =
        aspect.isFinite() && aspect > 0 && ids.all { id -> points.getOrNull(id)?.let {
            it.visibility.isFinite() && it.visibility >= ExerciseMotionPolicy.VISIBILITY && it.visibility <= 1f &&
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite() } == true }

    /** Image vertical, not calibrated gravity/spine flexion. Up/down orientation is explicit. */
    fun inclination(points: List<Landmark>, a: Int, b: Int, aspect: Double, upward: Boolean = true): Double? {
        if (!valid(points, aspect, a, b)) return null
        val dx = (points[a].x - points[b].x) * aspect
        val dy = if (upward) points[b].y - points[a].y else points[a].y - points[b].y
        if (abs(dx) + abs(dy) < 1e-6) return null
        return Math.toDegrees(atan2(abs(dx), dy.toDouble()))
    }

    /** Anatomical RIGHT is positive even if x is mirrored. Both anatomical axes must be visible. */
    fun signedLateral(points: List<Landmark>, aspect: Double): Double? {
        if (!valid(points, aspect, 11, 12, 23, 24)) return null
        val shoulderWidth = (points[12].x - points[11].x) * aspect
        val hipWidth = (points[24].x - points[23].x) * aspect
        if (abs(shoulderWidth) < ExerciseMotionPolicy.MIN_SHOULDER_AXIS_WIDTH || abs(hipWidth) < ExerciseMotionPolicy.MIN_HIP_AXIS_WIDTH || sign(shoulderWidth) != sign(hipWidth)) return null
        val dx = ((points[11].x + points[12].x - points[23].x - points[24].x) / 2) * aspect
        val dy = (points[23].y + points[24].y - points[11].y - points[12].y) / 2
        if (dy <= 1e-5) return null
        return Math.toDegrees(atan2(dx * sign(shoulderWidth), dy.toDouble())).takeIf { abs(it) <= ExerciseMotionPolicy.SIDE_BEND_MAX_MEASURABLE_DEG }
    }

    internal fun elbows(type: String, points: List<Landmark>, view: CameraView, aspect: Double): ExerciseMeasurement {
        if (view == CameraView.UNKNOWN) return ExerciseMeasurement(unavailable(view))
        val offset = PoseAngleExtractor.visibleSideOffset(points, type)
        fun elbow(side: Int): AngleMeasurement? = if (view == CameraView.FRONT || offset == side)
            JointAngleMeasurement.measure(points, listOf(11 + side, 13 + side, 15 + side), "elbow_interior", view = view,
                imageAspectRatio = aspect) else null
        val l = elbow(0); val r = elbow(1)
        val representative = listOfNotNull(l, r).filter { it.isEvaluable }.let { values ->
            if (type == "BICEP_CURL") values.minByOrNull { it.valueDeg!! } else values.maxByOrNull { it.minVisibility }
        } ?: unavailable(view)
        fun upper(side: Int) = if ((if (side == 0) l else r)?.isEvaluable == true)
            inclination(points, 13 + side, 11 + side, aspect, upward = type == "TRICEPS_EXTENSION") else null
        val line = if (type == "PUSH_UP" && view == CameraView.SIDE)
            JointAngleMeasurement.measure(points, listOf(11 + offset, 23 + offset, 27 + offset), "body_line_proxy",
                view = view, imageAspectRatio = aspect).valueDeg?.let { 180.0 - it } else null
        val shoulderTilt = if (type == "PUSH_UP" && view == CameraView.FRONT && valid(points, aspect, 11, 12)) {
            val dx = abs(points[11].x - points[12].x) * aspect
            if (dx > 1e-6) Math.toDegrees(atan2(abs(points[11].y - points[12].y).toDouble(), dx)) else null
        } else null
        return ExerciseMeasurement(representative, l?.valueDeg, r?.valueDeg, upper(0), upper(1),
            torsoInclination = if (view == CameraView.SIDE || (type == "BICEP_CURL" && view == CameraView.FRONT))
                inclination(points, 11 + offset, 23 + offset, aspect) else null,
            bodyLineDeviation = line, shoulderHeightDifference = shoulderTilt)
    }
}
class BicepCurlMeasurementStrategy : ExerciseMeasurementStrategy {
    override fun measure(points: List<Landmark>, view: CameraView, aspect: Double) = ExerciseMeasurements.elbows("BICEP_CURL", points, view, aspect)
}
class PushUpMeasurementStrategy : ExerciseMeasurementStrategy {
    override fun measure(points: List<Landmark>, view: CameraView, aspect: Double) = ExerciseMeasurements.elbows("PUSH_UP", points, view, aspect)
}
class BentOverRowMeasurementStrategy : ExerciseMeasurementStrategy {
    override fun measure(points: List<Landmark>, view: CameraView, aspect: Double) = ExerciseMeasurements.elbows("BENT_OVER_ROW", points, view, aspect)
}
class TricepsExtensionMeasurementStrategy : ExerciseMeasurementStrategy {
    override fun measure(points: List<Landmark>, view: CameraView, aspect: Double) = ExerciseMeasurements.elbows("TRICEPS_EXTENSION", points, view, aspect)
}
class SideBendMeasurementStrategy : ExerciseMeasurementStrategy {
    override fun measure(points: List<Landmark>, view: CameraView, aspect: Double): ExerciseMeasurement {
        val value = if (view == CameraView.FRONT) ExerciseMeasurements.signedLateral(points, aspect) else null
        return ExerciseMeasurement(AngleMeasurement("signed_lateral_inclination", value, CoordinateSpace.IMAGE_2D, view,
            if (value == null) MeasurementQuality.MISSING else MeasurementQuality.VALID,
            if (value == null) 0f else listOf(11, 12, 23, 24).minOf { points[it].visibility }, "INCLINATION_SIGNED"), lateralFlexion = value)
    }
}
