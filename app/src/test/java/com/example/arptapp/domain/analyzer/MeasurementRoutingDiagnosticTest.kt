package com.example.arptapp.domain.analyzer

import com.example.arptapp.domain.report.RepFrameFeatureExtractor
import org.junit.Assert.*
import org.junit.Test

class MeasurementRoutingDiagnosticTest {
    @Test fun elbowStrategiesUseVisibleSideAndNeverFabricateOppositeArm() {
        val p = SevenPoseFixtures.points().toMutableList()
        // Left interior = 180, right = 90. Make right the usable SIDE arm.
        p[15] = p[15].copy(x = p[13].x, y = .6f)
        for (id in listOf(11, 13, 15)) p[id] = p[id].copy(visibility = .7f)
        for (type in listOf("BICEP_CURL", "PUSH_UP", "BENT_OVER_ROW", "TRICEPS_EXTENSION")) {
            val side = ExerciseMeasurements.measure(type, p, CameraView.SIDE, 1.0)!!
            assertNull(side.leftElbow); assertEquals(90.0, side.rightElbow!!, .001)
            val front = ExerciseMeasurements.measure(type, p, CameraView.FRONT, 1.0)!!
            assertEquals(180.0, front.leftElbow!!, .001)
            assertEquals(90.0, front.rightElbow!!, .001)
            assertEquals(90.0, front.representative.valueDeg!!, .001) // curl=min; others=highest visibility
            val f = RepFrameFeatureExtractor.extract(p, type, CameraView.SIDE, 1.0, 1, side.representative.valueDeg)
            assertNull(f.leftElbow); assertEquals(side.rightElbow, f.rightElbow)
        }
    }
    @Test fun frontCurlChoosesMinimumButOtherElbowCountersUseVisibilityNotMinimum() {
        val p = SevenPoseFixtures.points().toMutableList()
        p[15] = p[15].copy(x = p[13].x, y = .6f)
        for (id in listOf(12, 14, 16)) p[id] = p[id].copy(visibility = .7f)
        assertEquals(90.0, ExerciseMeasurements.measure("BICEP_CURL", p, CameraView.FRONT)!!.representative.valueDeg!!, .001)
        for (type in listOf("PUSH_UP", "BENT_OVER_ROW", "TRICEPS_EXTENSION"))
            assertEquals(180.0, ExerciseMeasurements.measure(type, p, CameraView.FRONT)!!.representative.valueDeg!!, .001)
    }
    @Test fun sideBendNullReasonsUseSameGeometryAsMeasurementWithoutRelaxation() {
        fun check(reason: String, change: (MutableList<com.example.arptapp.data.model.Landmark>) -> Unit) {
            val p = SevenPoseFixtures.points().toMutableList(); change(p)
            val verdict = ExerciseMeasurements.lateralObservation(p, 1.0)
            assertEquals(reason, verdict.reason)
            assertNull(verdict.angle)
            assertNull(ExerciseMeasurements.signedLateral(p, 1.0))
        }
        check("LOW_VISIBILITY") { it[11] = it[11].copy(visibility = .64f) }
        check("SHOULDER_AXIS_TOO_NARROW") { it[12] = it[12].copy(x = it[11].x) }
        check("HIP_AXIS_TOO_NARROW") { it[24] = it[24].copy(x = it[23].x) }
        check("AXIS_SIGN_MISMATCH") { val a = it[11]; it[11] = it[12]; it[12] = a }
        check("TORSO_VERTICAL_DEGENERATE") { it[11] = it[11].copy(y = .8f); it[12] = it[12].copy(y = .8f) }
        check("OUTSIDE_60_DEG_RANGE") { it[11] = it[11].copy(x = 2f); it[12] = it[12].copy(x = 2.2f) }
    }
}
