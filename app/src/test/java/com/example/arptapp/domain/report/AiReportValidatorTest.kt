package com.example.arptapp.domain.report

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import com.example.arptapp.data.remote.*

class AiReportValidatorTest {
    private val rep = StructuredRepAnalysis(1, "SQUAT", "FRONT", 1200, PhaseDurations(),
        JointMetrics(leftKneeMinAngle = 90.0), AsymmetryMetrics(), emptyList(), emptyList(), emptyList(),
        LocalRepFeedback.forIssue(null), 80f, 8)
    private val input = WorkoutAnalysisAggregator.aggregate("SQUAT", listOf(rep))
    private val report = AiWorkoutReport("SQUAT", "1회가 기록되었습니다.", listOf("각도가 측정되었습니다."),
        listOf("다음에도 천천히 움직여 보세요."), "편안한 범위에서 움직여 보세요.", "촬영 방향을 유지해 보세요.",
        listOf(AiRepFeedback(1, "관찰된 각도를 확인해 보세요.", "회차 측정이 기록되었습니다.",
            "같은 방향으로 촬영해 보세요.", listOf("jointMetrics.leftKneeMinAngle"))))
    private fun encode(value: AiWorkoutReport) = Json.encodeToString(AiWorkoutReport.serializer(), value)
    private fun rejects(raw: String) { assertTrue(runCatching { AiReportValidator.decode(raw, input) }.isFailure) }
    @Test fun validReportUsesObservedEvidence() { assertEquals(report, AiReportValidator.decode(encode(report), input)) }
    @Test fun malformedMissingUnknownAndOversizedAreRejected() {
        rejects("not-json"); rejects("{}"); rejects(encode(report).dropLast(1) + ",\"extra\":true}")
        rejects(" ".repeat(32769))
    }
    @Test fun fabricatedRepNullEvidenceMedicalAndWrongExerciseRejected() {
        rejects(encode(report.copy(repFeedback = report.repFeedback.map { it.copy(repNumber = 2) })))
        rejects(encode(report.copy(repFeedback = report.repFeedback.map { it.copy(evidenceKeys = listOf("jointMetrics.torsoLeanMaxDeg")) })))
        rejects(encode(report.copy(summary = "치료가 필요합니다.")))
        rejects(encode(report.copy(summary = "숄더프레스가 관찰되었습니다.")))
        rejects(encode(report.copy(exerciseType = "SHOULDER_PRESS")))
    }
    @Test fun disabledRepositoryCannotInvokeTransport() = runBlocking {
        var calls = 0
        val repository = AiCoachingRepository(false, AiReportTransport { calls++; encode(report) })
        assertTrue(repository.generateWorkoutReport(input).exceptionOrNull() is AiUnavailableException)
        assertEquals(0, calls)
    }
    @Test fun inventedDegreeAndScoreAreRejectedEvenWithValidEvidenceKey() {
        rejects(encode(report.copy(repFeedback = report.repFeedback.map { it.copy(summary = "무릎 내각은 45°입니다.") })))
        rejects(encode(report.copy(summary = "90점이 기록되었습니다.")))
        assertEquals(report.copy(summary = "80점이 기록되었습니다."),
            AiReportValidator.decode(encode(report.copy(summary = "80점이 기록되었습니다.")), input))
    }
    @Test fun timeoutUsesLocalFallbackAndDoesNotRetry() = runBlocking {
        var calls = 0
        val slow = AiCoachingRepository(true, AiReportTransport {
            calls++; kotlinx.coroutines.delay(1000); encode(report)
        }, timeoutMs = 10)
        assertEquals(408, (slow.generateWorkoutReport(input).exceptionOrNull() as AiRequestException).status)
        assertEquals(1, calls)
    }
    @Test fun serverErrorsAndInvalidReportAreNotAiSuccess() = runBlocking {
        val offline = AiCoachingRepository(true, AiReportTransport { throw AiRequestException(503) })
        assertTrue(offline.generateWorkoutReport(input).isFailure)
        val invalid = AiCoachingRepository(true, AiReportTransport { "{}" })
        assertTrue(invalid.generateWorkoutReport(input).isFailure)
        val valid = AiCoachingRepository(true, AiReportTransport { encode(report) })
        assertEquals(report, valid.generateWorkoutReport(input).getOrThrow())
    }
}
