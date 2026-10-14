package com.example.arptapp.domain.report

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.counter.ExerciseCounter
import com.example.arptapp.data.model.StandardPose
import com.example.arptapp.presentation.report.StructuredReportPresentation
import org.junit.Assert.*
import org.junit.Test

class ShoulderPhaseStrategyTest {
    @Test fun bottomPreparationThenUpDoesNotInventReturn() {
        val rep = RepScenarioFixtures.press()
        assertEquals(PhaseDurations(null, 200, 500), rep.phaseDurations)
        assertEquals(listOf(MovementPhase.BOTTOM, MovementPhase.ASCENT), rep.errorEvents.map { it.phase })
        assertEquals(listOf(3, 5), rep.errorEvents.map { it.occurrenceFrames })
        assertTrue(rep.localPriorityFeedback.startsWith("올라가는 구간"))
    }
    @Test fun previousReturnIsNotDescentOfCurrentPress() {
        val rep = RepScenarioFixtures.press(listOf(170.0, 150.0, 120.0, 80.0, 81.0, 100.0, 130.0, 160.0, 170.0))
        assertNull(rep.phaseDurations.descentMs)
        assertTrue(rep.errorEvents.none { it.phase == MovementPhase.DESCENT })
    }
    @Test fun fullCycleFixtureSeparatesActualReturnEvenBelowInitialBottom() {
        val rep = RepScenarioFixtures.press(listOf(90.0, 90.0, 90.0, 120.0, 140.0, 170.0, 170.0, 170.0, 140.0, 120.0, 80.0))
        assertEquals(PhaseDurations(300, 200, 300), rep.phaseDurations)
        assertEquals(listOf(MovementPhase.BOTTOM, MovementPhase.ASCENT, MovementPhase.DESCENT), rep.errorEvents.map { it.phase })
        assertEquals(listOf(3, 3, 3), rep.errorEvents.map { it.occurrenceFrames })
    }
    @Test fun realCounterStillCompletesAtTopNotAtReturn() {
        val counter = ExerciseCounter()
        val engine = RepAnalysisEngine()
        val completed = mutableListOf<StructuredRepAnalysis>()
        val bottom = StandardPose.SHOULDER_BOTTOM_THRESHOLD
        listOf(bottom, bottom, bottom, 100.0, 120.0, 140.0, 160.0, 170.0, 170.0).forEachIndexed { i, a ->
            val time = i * 200L
            engine.observe(RepFrameFeatures(time, "SHOULDER_PRESS", CameraView.FRONT, a, leftElbow = a, rightElbow = a))
            if (counter.processAngle("SHOULDER_PRESS", a, time)) engine.complete(counter.getRepCount(), 80f)?.let(completed::add)
        }
        assertEquals(1, counter.getRepCount()); assertEquals(1, completed.size)
        assertNull(completed.single().phaseDurations.descentMs)
        assertTrue(completed.single().phaseDurations.ascentMs!! > 0)
    }
    @Test fun pressSideHasOnlyOneVisibleArmAndNoSymmetry() {
        val rep = RepScenarioFixtures.press(view = CameraView.SIDE)
        assertNotNull(rep.jointMetrics.leftElbowMaxAngle); assertNull(rep.jointMetrics.rightElbowMaxAngle)
        assertNull(rep.asymmetryMetrics.elbowAngleDifferenceDeg); assertTrue(rep.errorEvents.isEmpty())
    }
    @Test fun missingAscentTravelDoesNotInventPhaseEvents() {
        val s = listOf(80.0, 81.0, 85.0, 86.0).mapIndexed { i, a -> RepFrameFeatures(i * 100L, "SHOULDER_PRESS", CameraView.FRONT, a) }
        val segmented = RepPhaseSegmenter.segment(s, false)
        assertNull(segmented.durations.ascentMs); assertFalse(segmented.phases.contains(MovementPhase.ASCENT))
    }
    @Test fun pressReportFixtureShowsArmDifferenceAndUnmeasuredReturn() {
        val rep = RepScenarioFixtures.press()
        val text = StructuredReportPresentation.detail(rep)
        assertTrue(text.contains("좌우 팔꿈치 최대 차이 30.0°")); assertTrue(text.contains("올라가는 구간"))
        assertTrue(text.contains("유효 5프레임")); assertTrue(text.contains("복귀 구간은 아직 측정되지 않았습니다"))
        assertFalse(text.contains("ASCENT"))
    }
    @Test fun partialCaptureDoesNotCreateUnobservedDescent() {
        val engine = RepAnalysisEngine()
        repeat(400) { i -> engine.observe(RepFrameFeatures(i * 10L, "SHOULDER_PRESS", CameraView.FRONT,
            if (i < 300) 80.0 else 80.0 + (i - 300) * 0.9, leftElbow = 90.0, rightElbow = 50.0)) }
        val rep = engine.complete(1, null)!!
        assertTrue(rep.partialCapture); assertNull(rep.phaseDurations.descentMs); assertNull(rep.totalDurationMs)
        assertTrue(rep.errorEvents.none { it.phase == MovementPhase.DESCENT })
    }
    @Test fun failedLiftThenRetryDoesNotGuessPhaseOfCompletion() {
        val rep = RepScenarioFixtures.press(listOf(80.0, 80.0, 80.0, 110.0, 140.0, 110.0, 80.0, 110.0, 140.0, 170.0))
        assertEquals(PhaseDurations(), rep.phaseDurations)
        assertTrue(rep.errorEvents.isEmpty())
        assertTrue(StructuredReportPresentation.detail(rep).contains("단계를 확정할 근거가 부족"))
    }
}
