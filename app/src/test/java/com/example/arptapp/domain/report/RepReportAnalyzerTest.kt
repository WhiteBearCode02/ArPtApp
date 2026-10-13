package com.example.arptapp.domain.report

import com.example.arptapp.model.RepRecord
import org.junit.Assert.*
import org.junit.Test

class RepReportAnalyzerTest {
    private val analyzer = RepReportAnalyzer()

    @Test fun emptySessionIsExplicit() {
        val report = analyzer.report(emptyList(), "squat")
        assertEquals("SQUAT", report.exerciseType)
        assertEquals(0, report.totalReps)
        assertEquals(0, report.averageScore)
        assertTrue(report.feedbackMessage.contains("부족"))
    }

    @Test fun squatThresholdAndErrorPenaltyStayUnchanged() {
        val records = listOf(RepRecord(1, 90.0, 0f), RepRecord(2, 91.0, 0f, listOf("ERROR_KNEE_VALGUS")))
        assertEquals(listOf(100f, 65f), analyzer.analyze(records, "squat").map { it.score })
        assertEquals(82, analyzer.report(records, "SQUAT").averageScore)
    }

    @Test fun shoulderThresholdAndFallbackRepNumberStayUnchanged() {
        val analyses = analyzer.analyze(listOf(RepRecord(0, 159.0, 0f), RepRecord(9, 160.0, 0f)), "shoulder_press")
        assertEquals(listOf(1, 9), analyses.map { it.repNumber })
        assertEquals(listOf(75f, 100f), analyses.map { it.score })
        assertTrue(analyses[0].detail.contains("팔꿈치"))
    }

    @Test fun measuredPoseScoreDoesNotDependOnPhoneMovement() {
        val records = listOf(RepRecord(1, 82.0, 0f, poseScore = 84f), RepRecord(2, 82.0, 99f, poseScore = 84f))
        assertEquals(listOf(84f, 84f), analyzer.analyze(records, "SQUAT").map { it.score })
        assertTrue(analyzer.analyze(records, "SQUAT")[0].detail.contains("인증하지"))
    }

    @Test fun scoreCannotDropBelowZeroAndInputsAreNotMutated() {
        val errors = List(21) { "error_$it" }
        val records = listOf(RepRecord(1, 82.0, 0f, errors))
        assertEquals(0f, analyzer.analyze(records, "SQUAT").single().score)
        assertEquals(errors, records.single().errorTags)
    }
}
