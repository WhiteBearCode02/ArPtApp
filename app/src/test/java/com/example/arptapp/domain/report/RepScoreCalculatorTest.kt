package com.example.arptapp.domain.report

import com.example.arptapp.data.StructuredReportCodec
import com.example.arptapp.model.RepAnalysis
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class RepScoreCalculatorTest {
    private fun squat() = StructuredRepAnalysis(1, "SQUAT", "FRONT", 1200,
        PhaseDurations(500, 200, 500), JointMetrics(leftKneeMinAngle = 90.0, rightKneeMinAngle = 90.0),
        AsymmetryMetrics(kneeAngleDifferenceDeg = 0.0), emptyList(), emptyList(), emptyList(), "",
        observedFrames = 30, phaseModelVersion = 2)
    private fun score(rep: StructuredRepAnalysis) = RepScoreCalculator.calculate(rep).normalizedScore!!
    @Test fun engineeringReferenceSquatScoresHigherThanShallowSquat() {
        assertEquals(100f, score(squat()), .01f)
        assertTrue(score(squat()) > score(squat().copy(jointMetrics = JointMetrics(leftKneeMinAngle = 130.0, rightKneeMinAngle = 130.0))))
    }
    @Test fun measuredAsymmetryLowersScore() {
        assertTrue(score(squat()) > score(squat().copy(asymmetryMetrics = AsymmetryMetrics(kneeAngleDifferenceDeg = 25.0))))
    }
    @Test fun measuredSideLeanLowersScore() {
        val side = squat().copy(cameraView = "SIDE", jointMetrics = JointMetrics(leftKneeMinAngle = 90.0, torsoLeanMaxDeg = 5.0))
        assertTrue(score(side) > score(side.copy(jointMetrics = side.jointMetrics.copy(torsoLeanMaxDeg = 40.0))))
    }
    @Test fun unavailableSideSymmetryExcludedNotZero() {
        val side = squat().copy(cameraView = "SIDE", jointMetrics = JointMetrics(leftKneeMinAngle = 90.0, torsoLeanMaxDeg = 5.0), asymmetryMetrics = AsymmetryMetrics())
        val result = RepScoreCalculator.calculate(side)
        assertEquals(75f, result.availableWeight, .01f)
        assertEquals(100f, result.normalizedScore!!, .01f)
        assertNull(result.components.single { it.component == RepScoreComponent.SYMMETRY }.earned)
    }
    @Test fun missingMetricDoesNotReduceNormalizedScore() {
        val result = RepScoreCalculator.calculate(squat().copy(asymmetryMetrics = AsymmetryMetrics()))
        assertEquals(55f, result.availableWeight, .01f)
        assertEquals(100f, result.normalizedScore!!, .01f)
    }
    @Test fun noReferencePressHasLocalScoreAndNoDescentPenalty() {
        val press = squat().copy(exerciseType = "SHOULDER_PRESS", phaseDurations = PhaseDurations(ascentMs = 500),
            jointMetrics = JointMetrics(leftElbowMaxAngle = 170.0, rightElbowMaxAngle = 170.0),
            asymmetryMetrics = AsymmetryMetrics(elbowAngleDifferenceDeg = 0.0))
        val scored = RepScoreCalculator.score(press)
        assertEquals(100f, scored.score!!, .01f)
        assertNull(scored.referenceSimilarity)
        assertEquals(15f, scored.localScore!!.components.single { it.component == RepScoreComponent.PHASE_COMPLETENESS }.earned!!, .01f)
        assertTrue(score(press) > score(press.copy(jointMetrics = JointMetrics(leftElbowMaxAngle = 135.0, rightElbowMaxAngle = 135.0))))
    }
    @Test fun dtwSimilarityNeverChangesLocalScore() {
        assertEquals(RepScoreCalculator.score(squat(), 1f).score, RepScoreCalculator.score(squat(), 99f).score)
        assertEquals(1f, RepScoreCalculator.score(squat(), 1f).referenceSimilarity!!, .01f)
        assertNull(RepScoreCalculator.score(squat(), Float.NaN).referenceSimilarity)
    }
    @Test fun severityAndExposureIncreasePenalty() {
        fun penalty(severity: Int, frames: Int) = RepScoreCalculator.calculate(squat().copy(errorEvents =
            listOf(FormErrorEvent(FormErrorType.KNEE_VALGUS, severity = severity, occurrenceFrames = frames)))).penalties.sumOf { it.points.toDouble() }
        assertTrue(penalty(3, 10) > penalty(1, 10))
        assertTrue(penalty(2, 15) > penalty(2, 3))
    }
    @Test fun duplicateIssueAcrossPhasesAndSidesIsCapped() {
        val events = MovementPhase.entries.flatMap { phase -> BodySide.entries.map { side ->
            FormErrorEvent(FormErrorType.KNEE_VALGUS, phase, side, severity = 3, occurrenceFrames = 30)
        } }
        val result = RepScoreCalculator.calculate(squat().copy(errorEvents = events + events))
        assertEquals(1, result.penalties.size)
        assertEquals(3f, result.penalties.single().points, .01f)
    }
    @Test fun totalErrorPenaltyIsBoundedByTen() {
        val result = RepScoreCalculator.calculate(squat().copy(errorEvents = FormErrorType.entries.map {
            FormErrorEvent(it, severity = 3, occurrenceFrames = 30)
        }))
        assertEquals(10.0, result.penalties.sumOf { it.points.toDouble() }, .001)
        assertTrue(result.penalties.all { it.points <= 3f })
        assertEquals(0f, result.components.single { it.component == RepScoreComponent.ERROR_EVENTS }.earned!!, .01f)
    }
    @Test fun briefNoiseIsNotDeducted() {
        val result = RepScoreCalculator.calculate(squat().copy(errorEvents = listOf(
            FormErrorEvent(FormErrorType.KNEE_VALGUS, severity = 3, occurrenceFrames = 2))))
        assertTrue(result.penalties.isEmpty())
    }
    @Test fun missingMotionEvidenceDoesNotCreateZeroOrPerfectScore() {
        assertNull(RepScoreCalculator.calculate(squat().copy(jointMetrics = JointMetrics())).normalizedScore)
        assertNull(RepScoreCalculator.calculate(squat().copy(observedFrames = 2)).normalizedScore)
        assertNull(RepScoreCalculator.calculate(squat().copy(exerciseType = "LUNGE")).normalizedScore)
        assertNull(RepScoreCalculator.calculate(squat().copy(cameraView = "UNKNOWN")).normalizedScore)
    }
    @Test fun partialPhaseIsUnavailableNotZero() {
        val result = RepScoreCalculator.calculate(squat().copy(partialCapture = true))
        assertNull(result.components.single { it.component == RepScoreComponent.PHASE_COMPLETENESS }.earned)
        assertEquals(100f, result.normalizedScore!!, .01f)
    }
    @Test fun localBreakdownPersistsInExistingJsonEnvelope() {
        val rep = RepScoreCalculator.score(squat(), 65f)
        val workout = WorkoutAnalysisAggregator.aggregate("SQUAT", listOf(rep))
        val raw = StructuredReportCodec.encode(listOf(RepAnalysis(1, rep.score!!, "")), workout)
        assertEquals(workout, StructuredReportCodec.decode(raw))
        assertTrue(raw.contains("localScore"))
        assertTrue(raw.contains("referenceSimilarity"))
    }
    @Test fun strictNetworkShapeOmitsLocalMetadataEvenWithEncodeDefaults() {
        val input = WorkoutAnalysisAggregator.aggregate("SQUAT", listOf(RepScoreCalculator.score(squat(), 65f)))
        val json = Json { encodeDefaults = true; explicitNulls = true }
        val raw = json.encodeToJsonElement(StructuredWorkoutAnalysis.serializer(), WorkoutAnalysisAggregator.forNetwork(input)).toString()
        assertFalse(raw.contains("localScore"))
        assertFalse(raw.contains("referenceSimilarity"))
        assertTrue(raw.contains("phaseModelVersion"))
    }
    @Test fun oldJsonKeepsHistoricalScoreWithoutRescoring() {
        val old = squat().copy(score = 78f)
        val raw = StructuredReportCodec.encode(listOf(RepAnalysis(1, 78f, "legacy")), WorkoutAnalysisAggregator.aggregate("SQUAT", listOf(old)))
        val restored = StructuredReportCodec.decode(raw)!!.repAnalyses.single()
        assertEquals(78f, restored.score!!, .01f)
        assertNull(restored.localScore)
        assertFalse(raw.contains("localScore"))
    }
    @Test fun diagnosticAllowsOnlyWhitelistedScalars() {
        val text = RepDiagnosticFormatter.format(RepScoreCalculator.score(squat()), "secret@example.invalid", "token-secret", Double.NaN)
        assertTrue(text.contains("sessionExerciseType=UNKNOWN"))
        assertTrue(text.contains("localRepScore=100.0"))
        assertFalse(text.contains("@"))
        assertFalse(text.contains("token-secret"))
        assertFalse(text.contains("NaN"))
    }
}
