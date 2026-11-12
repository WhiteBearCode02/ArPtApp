package com.example.arptapp.domain.analyzer

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.data.model.PoseData
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.report.RepFrameFeatureExtractor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

object SevenPoseFixtures {
    fun points(lean: Float = 0f): List<Landmark> {
        val p = MutableList(33) { Landmark(.5f,.5f,0f,1f) }
        fun set(i: Int,x: Float,y: Float) { p[i] = Landmark(x,y,0f,1f) }
        set(11,.4f+lean,.2f);set(12,.6f+lean,.2f)
        set(13,.4f+lean,.4f);set(14,.6f+lean,.4f)
        set(15,.2f+lean,.4f);set(16,.8f+lean,.4f)
        set(23,.4f,.6f);set(24,.6f,.6f)
        set(25,.4f,.8f);set(26,.6f,.8f);set(27,.4f,1f);set(28,.6f,1f)
        return p
    }
    fun pose(points: List<Landmark>) = PoseData(0,points,emptyMap())
}

@RunWith(Parameterized::class)
class ExpandedMeasurementTest(private val type: String) {
    companion object { @JvmStatic @Parameterized.Parameters(name="{0}") fun types() = ExerciseType.supported.drop(2).map { arrayOf(it.name) } }
    @Test fun expectedProjectedAngleAndDefinition() {
        val m = PoseAngleExtractor.measurePose(type,SevenPoseFixtures.pose(SevenPoseFixtures.points()),CameraView.FRONT)
        assertTrue(m.isEvaluable); assertEquals(if(type=="SIDE_BEND") 0.0 else 90.0,m.valueDeg!!,.001)
        assertEquals(CoordinateSpace.IMAGE_2D,m.coordinateSpace)
    }
    @Test fun missingLandmarksStayNull() {
        assertNull(PoseAngleExtractor.measurePose(type,SevenPoseFixtures.pose(emptyList()),CameraView.FRONT).valueDeg)
    }
    @Test fun lowVisibilityIsNotZero() {
        val points = SevenPoseFixtures.points().map { it.copy(visibility=.1f) }
        assertNull(PoseAngleExtractor.measurePose(type,SevenPoseFixtures.pose(points),CameraView.FRONT).valueDeg)
    }
    @Test fun unknownOrInvalidAspectNeverInventsMeasurement() {
        val p = SevenPoseFixtures.pose(SevenPoseFixtures.points())
        assertNull(PoseAngleExtractor.measurePose(type,p,CameraView.UNKNOWN).valueDeg)
        assertNull(PoseAngleExtractor.measurePose(type,p,CameraView.FRONT,Double.NaN).valueDeg)
    }
    @Test fun sideViewNeverExposesBilateralOrFrontalProxy() {
        val f = RepFrameFeatureExtractor.extract(SevenPoseFixtures.points(),type,CameraView.SIDE,1.0,0,null)
        assertNull(f.rightElbow);assertNull(f.shoulderHeightDifference);assertNull(f.lateralFlexion)
        if(type=="SIDE_BEND") assertNull(PoseAngleExtractor.measurePose(type,SevenPoseFixtures.pose(SevenPoseFixtures.points()),CameraView.SIDE).valueDeg)
    }
    @Test fun mirrorPreservesAnatomicalSideAndMagnitude() {
        val p = SevenPoseFixtures.points(.1f)
        val a = PoseAngleExtractor.measurePose(type,SevenPoseFixtures.pose(p),CameraView.FRONT)
        val b = PoseAngleExtractor.measurePose(type,SevenPoseFixtures.pose(p.map { it.copy(x=1f-it.x) }),CameraView.FRONT)
        assertEquals(a.valueDeg!!,b.valueDeg!!,.0001)
        if(type=="SIDE_BEND") assertTrue(a.valueDeg!!>0)
    }
}

class SideBendProjectionTest {
    @Test fun anatomicalLeftIsNegativeEvenAfterImageMirror() {
        val p = SevenPoseFixtures.points(-.1f)
        assertTrue(ExerciseMeasurements.signedLateral(p,1.0)!!<0)
        assertTrue(ExerciseMeasurements.signedLateral(p.map {it.copy(x=1f-it.x)},1.0)!!<0)
    }
    @Test fun degenerateOrOccludedShoulderAxisAbstains() {
        val p = SevenPoseFixtures.points().toMutableList()
        p[12]=p[12].copy(x=p[11].x)
        assertNull(ExerciseMeasurements.signedLateral(p,1.0))
    }
}
