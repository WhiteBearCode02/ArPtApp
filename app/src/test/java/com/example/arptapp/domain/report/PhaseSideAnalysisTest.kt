package com.example.arptapp.domain.report

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.presentation.report.StructuredReportPresentation
import org.junit.Assert.*
import org.junit.Test

class PhaseSideAnalysisTest {
    private val descent = (0..4).toSet()
    private val bottom = (5..8).toSet()
    private val ascent = (9..13).toSet()
    private fun events(indices: Set<Int>) = RepScenarioFixtures.squat(right = indices).errorEvents
    @Test fun leftOnly() { assertEquals(BodySide.LEFT, RepScenarioFixtures.squat(left = descent).errorEvents.single().side) }
    @Test fun rightOnly() { assertEquals(BodySide.RIGHT, events(descent).single().side) }
    @Test fun bothProduceSeparateEvents() {
        val result = RepScenarioFixtures.squat(descent, descent).errorEvents
        assertEquals(setOf(BodySide.LEFT, BodySide.RIGHT), result.map { it.side }.toSet()); assertEquals(2, result.size)
    }
    @Test fun neitherDoesNotCreateStrengthFromMissingErrors() {
        val rep = RepScenarioFixtures.squat()
        assertTrue(rep.errorEvents.isEmpty())
        assertTrue(rep.strengths.contains("KNEE_SYMMETRY_OBSERVED")) // measured difference, not just no errors
        assertFalse(StructuredReportPresentation.detail(rep).contains("완벽"))
    }
    @Test fun descentOnly() { assertEquals(MovementPhase.DESCENT, events(descent).single().phase) }
    @Test fun bottomOnly() { assertEquals(MovementPhase.BOTTOM, events(bottom).single().phase) }
    @Test fun ascentOnly() { assertEquals(MovementPhase.ASCENT, events(ascent).single().phase) }
    @Test fun descentAndBottomRemainSeparate() {
        val e = events(descent + bottom)
        assertEquals(listOf(MovementPhase.DESCENT, MovementPhase.BOTTOM), e.map { it.phase })
        assertEquals(listOf(5, 4), e.map { it.occurrenceFrames }); assertEquals(listOf(0L, 500L), e.map { it.timestampMs })
    }
    @Test fun bottomAndAscentRemainSeparate() {
        val e = events(bottom + ascent)
        assertEquals(listOf(MovementPhase.BOTTOM, MovementPhase.ASCENT), e.map { it.phase })
        assertEquals(listOf(4, 5), e.map { it.occurrenceFrames })
    }
    @Test fun oneFrameIsNoise() { assertTrue(events(setOf(1)).isEmpty()) }
    @Test fun twoFramesAreNoise() { assertTrue(events(setOf(1, 2)).isEmpty()) }
    @Test fun threeContinuousFramesQualify() {
        val e = events(setOf(1, 2, 3)).single()
        assertEquals(3, e.occurrenceFrames); assertEquals(100L, e.timestampMs)
    }
    @Test fun intermittentThreeDoNotQualify() { assertTrue(events(setOf(0, 2, 4)).isEmpty()) }
    @Test fun phaseBoundaryDoesNotBorrowAdjacentFrames() { assertTrue(events(setOf(3, 4, 5, 6)).isEmpty()) }
    @Test fun qualifyingRunDoesNotIncludeLaterTwoFrameNoise() {
        val samples = List(9) { RepFrameFeatures(it * 100L, "SQUAT", CameraView.FRONT, 90.0) }
        val e = FormEventAggregator.collect(samples, List(9) { MovementPhase.DESCENT }, FormErrorType.KNEE_VALGUS, BodySide.RIGHT) {
            if ((it.timestampMs / 100).toInt() in setOf(0, 1, 2, 4, 5)) 2 else 0
        }.single()
        assertEquals(3, e.occurrenceFrames); assertEquals(0L, e.timestampMs)
    }
    @Test fun multipleAcceptedRunsAggregateOnlyTheirValidFrames() {
        val samples = List(8) { RepFrameFeatures(it * 100L, "SQUAT", CameraView.FRONT, 90.0) }
        val e = FormEventAggregator.collect(samples, List(8) { MovementPhase.DESCENT }, FormErrorType.KNEE_VALGUS, BodySide.LEFT) {
            if (it.timestampMs != 300L && it.timestampMs != 700L) 1 else 0
        }.single()
        assertEquals(6, e.occurrenceFrames)
    }
    @Test fun lowSeverityUsesInitialBands() {
        assertEquals(1, RepScenarioFixtures.squat(right = descent, ratio = 0.10).errorEvents.single().severity)
        assertEquals(1, EngineeringFormPolicy.asymmetrySeverity(20.0)); assertEquals(1, EngineeringFormPolicy.leanSeverity(25.0))
    }
    @Test fun mediumSeverityUsesInitialBands() {
        assertEquals(2, RepScenarioFixtures.squat(right = descent, ratio = 0.20).errorEvents.single().severity)
        assertEquals(2, EngineeringFormPolicy.asymmetrySeverity(30.0)); assertEquals(2, EngineeringFormPolicy.leanSeverity(35.0))
    }
    @Test fun highSeverityUsesInitialBands() {
        assertEquals(3, RepScenarioFixtures.squat(right = descent, ratio = 0.30).errorEvents.single().severity)
        assertEquals(3, EngineeringFormPolicy.asymmetrySeverity(40.0)); assertEquals(3, EngineeringFormPolicy.leanSeverity(45.0))
    }
    @Test fun thresholdAndNonFiniteValuesDoNotBecomeIssues() {
        assertEquals(0, EngineeringFormPolicy.valgusSeverity(0.08)); assertEquals(0, EngineeringFormPolicy.asymmetrySeverity(15.0))
        assertEquals(0, EngineeringFormPolicy.leanSeverity(20.0)); assertEquals(0, EngineeringFormPolicy.asymmetrySeverity(Double.NaN))
        assertEquals(0, EngineeringFormPolicy.valgusSeverity(Double.NaN))
    }
    @Test fun unknownViewSuppressesAllViewDependentMetricsAndEvents() {
        val rep = RepScenarioFixtures.squat(descent, descent, difference = 30.0, view = CameraView.UNKNOWN, lean = 45.0)
        assertEquals(JointMetrics(), rep.jointMetrics); assertEquals(AsymmetryMetrics(), rep.asymmetryMetrics); assertTrue(rep.errorEvents.isEmpty())
    }
    @Test fun sideSquatSuppressesBilateralKneesAndValgus() {
        val rep = RepScenarioFixtures.squat(descent, descent, view = CameraView.SIDE, lean = 35.0)
        assertNull(rep.jointMetrics.rightKneeMinAngle); assertNull(rep.asymmetryMetrics.kneeAngleDifferenceDeg)
        assertTrue(rep.errorEvents.all { it.type == FormErrorType.FORWARD_LEAN && it.severity == 2 })
    }
    @Test fun squatReportFixtureIncludesActualSidePhaseCountAndShortTts() {
        val rep = RepScenarioFixtures.squat(right = descent + bottom, difference = 12.4)
        val text = StructuredReportPresentation.detail(rep)
        assertTrue(text.contains("오른쪽 무릎")); assertTrue(text.contains("내려가는 구간")); assertTrue(text.contains("하단 구간"))
        assertTrue(text.contains("유효 5프레임")); assertTrue(text.contains("12.4°"))
        assertTrue(rep.localPriorityFeedback.contains("오른쪽")); assertTrue(rep.localPriorityFeedback.length < 60)
        assertFalse(text.contains("DESCENT")); assertFalse(text.contains("RIGHT"))
    }
    @Test fun diagnosticHasOnlyStructuredRelativeData() {
        val rep = RepScenarioFixtures.squat(right = descent)
        val text = RepDiagnosticFormatter.format(rep)
        assertTrue(text.contains("KNEE_VALGUS/RIGHT/DESCENT")); assertTrue(text.contains("relativeMs=0"))
        listOf("landmark", "email", "userId", "token", "key", "10000").forEach { assertFalse(text.contains(it)) }
    }
    @Test fun delayedFrameGapPreservesPartialRatherThanClaimingFullRep() {
        val engine = RepAnalysisEngine()
        engine.observe(RepFrameFeatures(1000, "SQUAT", CameraView.FRONT, 170.0))
        listOf(120.0, 100.0, 80.0, 90.0, 130.0, 170.0).forEachIndexed { i, a ->
            engine.observe(RepFrameFeatures(3000 + i * 100L, "SQUAT", CameraView.FRONT, a, leftKnee = a, rightKnee = a))
        }
        val rep = engine.complete(1, null)!!
        assertTrue(rep.partialCapture); assertNull(rep.totalDurationMs); assertNull(rep.phaseDurations.descentMs)
        assertTrue(rep.errorEvents.none { it.phase == MovementPhase.DESCENT })
    }
}
