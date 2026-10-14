package com.example.arptapp.domain.report

import com.example.arptapp.data.model.Landmark
import com.example.arptapp.domain.analyzer.CameraView
import org.junit.Assert.*
import org.junit.Test

class RepAnalysisEngineTest {
    private fun run(exercise: String = "SQUAT", view: CameraView = CameraView.FRONT,
                    angles: List<Double> = listOf(170.0, 140.0, 100.0, 80.0, 81.0, 100.0, 140.0, 170.0),
                    difference: Double = 0.0): StructuredRepAnalysis {
        val engine = RepAnalysisEngine()
        angles.forEachIndexed { i, a -> engine.observe(RepFrameFeatures(i * 200L, exercise, view, a,
            leftKnee = a, rightKnee = a - difference, leftElbow = a, rightElbow = a - difference)) }
        return engine.complete(1, 80f)!!
    }
    @Test fun phaseTimingAndBilateralMetrics() {
        val rep = run(difference = 20.0)
        assertEquals(600L, rep.phaseDurations.descentMs)
        assertEquals(200L, rep.phaseDurations.bottomMs)
        assertEquals(600L, rep.phaseDurations.ascentMs)
        assertEquals(listOf(MovementPhase.DESCENT, MovementPhase.ASCENT), rep.errorEvents.map { it.phase })
        assertTrue(rep.errorEvents.all { it.type == FormErrorType.KNEE_ASYMMETRY && it.occurrenceFrames == 3 })
        assertEquals(20.0, rep.asymmetryMetrics.kneeAngleDifferenceDeg!!, 0.01)
        assertNull(rep.jointMetrics.leftHipMinAngle)
        assertNull(rep.jointMetrics.torsoLeanMaxDeg)
    }
    @Test fun shoulderPressUsesElbowsNotKnees() {
        val rep = run("SHOULDER_PRESS", difference = 20.0)
        assertNull(rep.jointMetrics.leftKneeMinAngle)
        assertEquals(170.0, rep.jointMetrics.leftElbowMaxAngle!!, 0.01)
        assertEquals(FormErrorType.ELBOW_ASYMMETRY, rep.errorEvents.single().type)
    }
    @Test fun sideDoesNotClaimSymmetryOrFrontalValgus() {
        val rep = run(view = CameraView.SIDE, difference = 30.0)
        assertNull(rep.asymmetryMetrics.kneeAngleDifferenceDeg)
        assertTrue(rep.errorEvents.isEmpty())
    }
    @Test fun partialStartingBottomDoesNotInventDescent() {
        assertNull(run(angles = listOf(80.0, 81.0, 100.0, 140.0, 170.0)).phaseDurations.descentMs)
    }
    @Test fun invalidAngleAndOcclusionResetPendingRep() {
        val engine = RepAnalysisEngine()
        listOf(170.0, 120.0, Double.NaN, 90.0).forEachIndexed { i, a ->
            engine.observe(RepFrameFeatures(i * 100L, "SQUAT", CameraView.FRONT, a)) }
        assertNull(engine.complete(1, 90f))
    }
    @Test fun boundedBufferMarksPartialAndResetClears() {
        val engine = RepAnalysisEngine()
        repeat(400) { engine.observe(RepFrameFeatures(it * 10L, "SQUAT", CameraView.FRONT,
            if (it < 300) 170.0 - it / 3.0 else 70.0 + (it - 300))) }
        val rep = engine.complete(1, null)!!
        assertTrue(rep.partialCapture)
        assertNull(rep.totalDurationMs)
        assertNull(rep.phaseDurations.descentMs)
        assertTrue(rep.observedFrames <= 300)
        assertNull(engine.complete(2, null))
    }
    @Test fun extractorMissingLowVisibilityAndUnknownAreNotZeroAngles() {
        assertNull(RepFrameFeatureExtractor.extract(emptyList(), "SQUAT", CameraView.FRONT, 1.0, 0, 90.0).leftKnee)
        val points = List(33) { Landmark(it / 33f, it / 34f, 0f, 0.2f) }
        assertNull(RepFrameFeatureExtractor.extract(points, "SQUAT", CameraView.SIDE, 1.0, 0, 90.0).torsoLean)
        assertNull(RepFrameFeatureExtractor.extract(points, "SQUAT", CameraView.UNKNOWN, 1.0, 0, 90.0).leftKnee)
    }
    @Test fun aggregationLimitsOnlyNetworkDetails() {
        val reps = (1..70).map { run().copy(repNumber = it) }
        val report = WorkoutAnalysisAggregator.aggregate("SQUAT", reps)
        val network = WorkoutAnalysisAggregator.forNetwork(report)
        assertEquals(70, report.repAnalyses.size)
        assertEquals(50, network.repAnalyses.size)
        assertEquals(20, network.omittedReps)
        assertEquals(70, network.totalReps)
    }
    @Test fun eventsNeedQualityAndPriorityUsesSeverityBeforeFrequency() {
        val engine = RepAnalysisEngine()
        listOf(170.0, 150.0, 130.0, 110.0, 100.0, 80.0, 81.0, 100.0, 140.0, 170.0).forEachIndexed { i, a ->
            engine.observe(RepFrameFeatures(i * 200L, "SQUAT", CameraView.FRONT, a,
                leftKnee = a, rightKnee = a - 20, kneeValgusObserved = i in 2..4))
        }
        val rep = engine.complete(1, 70f)!!
        assertEquals(3, rep.errorEvents.size)
        val valgus = rep.errorEvents.single { it.type == FormErrorType.KNEE_VALGUS }
        assertEquals(3, valgus.occurrenceFrames)
        assertEquals(2, valgus.severity)
        assertEquals(LocalRepFeedback.forEvent(valgus), rep.localPriorityFeedback)
        assertEquals(MovementPhase.DESCENT, valgus.phase)
        val hidden = RepAnalysisEngine()
        listOf(170.0, 120.0, 80.0, 90.0, 140.0, 170.0).forEachIndexed { i, a -> hidden.observe(
            RepFrameFeatures(i * 200L, "SQUAT", CameraView.FRONT, a, leftKnee = a, kneeValgusObserved = true)) }
        assertTrue(hidden.complete(1, null)!!.errorEvents.isEmpty())
    }
    @Test fun validSideLeanIsNotEvaluatedFromFrontAndOcclusionIsNull() {
        val engine = RepAnalysisEngine()
        listOf(170.0, 120.0, 80.0, 90.0, 140.0, 170.0).forEachIndexed { i, a -> engine.observe(
            RepFrameFeatures(i * 200L, "SQUAT", CameraView.SIDE, a, leftKnee = a, torsoLean = 25.0)) }
        val rep = engine.complete(1, null)!!
        assertEquals(FormErrorType.FORWARD_LEAN, rep.errorEvents.single().type)
        assertNull(rep.errorEvents.single().side)
        assertNull(rep.asymmetryMetrics.kneeAngleDifferenceDeg)
    }
}
