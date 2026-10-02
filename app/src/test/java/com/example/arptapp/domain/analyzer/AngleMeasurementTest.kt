package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.model.Landmark
import org.junit.Assert.*
import org.junit.Test

class AngleMeasurementTest {
    private val points = listOf(Landmark(1f, 0f, 0f, 1f), Landmark(0f, 0f, 0f, 1f), Landmark(0f, 1f, 0f, 1f))
    @Test fun rightAngleRetainsDefinitionAndQuality() {
        val angle = JointAngleMeasurement.measure(points, listOf(0, 1, 2), "knee")
        assertEquals(90.0, angle.valueDeg!!, 0.001)
        assertEquals(90.0, angle.flexionDeg!!, 0.001)
        assertEquals(CoordinateSpace.IMAGE_2D, angle.coordinateSpace)
        assertTrue(angle.isEvaluable)
    }
    @Test fun occlusionMissingAndDegenerateAreNotNormal() {
        assertEquals(MeasurementQuality.LOW_VISIBILITY, JointAngleMeasurement.measure(points.map { it.copy(visibility = 0.2f) }, listOf(0, 1, 2), "knee").quality)
        assertEquals(MeasurementQuality.MISSING, JointAngleMeasurement.measure(emptyList(), listOf(0, 1, 2), "knee").quality)
        val angle = JointAngleMeasurement.measure(List(3) { points[0] }, listOf(0, 1, 2), "knee")
        assertNull(angle.valueDeg)
        assertFalse(angle.isEvaluable)
    }
    @Test fun nonFiniteAndPixelAspectAreHandled() {
        assertEquals(MeasurementQuality.NON_FINITE, JointAngleMeasurement.measure(points.map { it.copy(x = Float.NaN) }, listOf(0, 1, 2), "knee").quality)
        val p = listOf(Landmark(1f, 1f, 0f, 1f), points[1], points[2])
        assertEquals(63.4349, JointAngleMeasurement.measure(p, listOf(0, 1, 2), "knee", imageAspectRatio = 2.0).valueDeg!!, 0.01)
    }
}
