package com.example.arptapp.domain.report

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class DirectionalClaimValidatorTest {
    private val rep = RepScenarioFixtures.squat(right = (0..4).toSet())
    private val input = WorkoutAnalysisAggregator.aggregate("SQUAT", listOf(rep), 3)
    private val event = rep.errorEvents.single()
    private val valid = AiWorkoutReport("SQUAT", "오른쪽 무릎이 내려가는 구간에서 관찰되었습니다.", emptyList(),
        listOf("무릎 방향을 확인해 보세요."), "오른쪽 무릎 방향을 먼저 확인해 보세요.", "천천히 진행해 보세요.",
        listOf(AiRepFeedback(3, "내려가는 구간에서 오른쪽 무릎의 안쪽 이동 후보가 관찰되었습니다.",
            "측정 정보가 기록되었습니다.", "오른쪽 무릎 방향을 확인해 보세요.", listOf(DirectionalClaimValidator.key(event)))))
    private fun check(report: AiWorkoutReport, data: StructuredWorkoutAnalysis = input) =
        AiReportValidator.decode(Json.encodeToString(AiWorkoutReport.serializer(), report), data)
    private fun rejected(report: AiWorkoutReport, data: StructuredWorkoutAnalysis = input) {
        assertTrue(runCatching { check(report, data) }.isFailure)
    }
    @Test fun validSideAndPhaseAccepted() { assertEquals(valid, check(valid)) }
    @Test fun wrongSideRejectedInRepAndSummary() {
        rejected(valid.copy(repFeedback = valid.repFeedback.map { it.copy(summary = "내려가는 구간에서 왼쪽 무릎이 관찰되었습니다.") }))
        rejected(valid.copy(summary = "왼쪽 무릎이 관찰되었습니다."))
    }
    @Test fun wrongPhaseRejectedInRepAndSummary() {
        rejected(valid.copy(repFeedback = valid.repFeedback.map { it.copy(summary = "올라가는 구간에서 오른쪽 무릎이 관찰되었습니다.") }))
        rejected(valid.copy(summary = "하단 구간에서 오른쪽 무릎이 관찰되었습니다."))
    }
    @Test fun nullSideCannotAuthorizeLeftOrRight() {
        val unknown = rep.copy(errorEvents = listOf(event.copy(side = null)))
        val data = input.copy(repAnalyses = listOf(unknown))
        rejected(valid.copy(repFeedback = valid.repFeedback.map { it.copy(evidenceKeys = listOf("issue:KNEE_VALGUS")) }), data)
    }
    @Test fun unrelatedMetricReferenceCannotAuthorizeAnIssueSide() {
        rejected(valid.copy(repFeedback = valid.repFeedback.map { it.copy(evidenceKeys = listOf("jointMetrics.rightKneeMinAngle")) }))
    }
    @Test fun sideAndPhaseMustBelongToSameEvent() {
        val crossed = rep.copy(errorEvents = listOf(event, event.copy(side = BodySide.LEFT, phase = MovementPhase.ASCENT)))
        val report = valid.copy(repFeedback = valid.repFeedback.map { it.copy(summary = "올라가는 구간에서 오른쪽 무릎이 관찰되었습니다.", evidenceKeys = listOf("issue:KNEE_VALGUS")) })
        rejected(report, input.copy(repAnalyses = listOf(crossed)))
    }
    @Test fun evidenceForOnlyOnePhaseCannotAuthorizeAnotherActualPhase() {
        val both = rep.copy(errorEvents = listOf(event, event.copy(phase = MovementPhase.BOTTOM)))
        val report = valid.copy(repFeedback = valid.repFeedback.map { it.copy(summary = "하단 구간에서 오른쪽 무릎이 관찰되었습니다.") })
        rejected(report, input.copy(repAnalyses = listOf(both)))
    }
    @Test fun legacyTypeEvidenceStillWorksWithoutInventingSides() {
        assertEquals(valid.copy(repFeedback = valid.repFeedback.map { it.copy(evidenceKeys = listOf("issue:KNEE_VALGUS")) }),
            check(valid.copy(repFeedback = valid.repFeedback.map { it.copy(evidenceKeys = listOf("issue:KNEE_VALGUS")) })))
    }
    @Test fun actualEventFramesAndPriorityAcceptedButInventedCountRejected() {
        val real = valid.copy(repFeedback = valid.repFeedback.map { it.copy(summary = "내려가는 구간에서 오른쪽 무릎 후보가 5프레임 관찰되었습니다. 초기 우선도 2입니다.") })
        assertEquals(real, check(real))
        rejected(real.copy(repFeedback = real.repFeedback.map { it.copy(summary = "오른쪽 무릎 후보가 6프레임 관찰되었습니다.") }))
    }
    @Test fun numberedSummaryCannotBorrowAnotherRepetitionsSide() {
        val other = rep.copy(repNumber = 2, errorEvents = listOf(event.copy(side = BodySide.LEFT)))
        val data = input.copy(repAnalyses = listOf(rep, other))
        assertTrue(runCatching { DirectionalClaimValidator.validate(listOf("3회차에서 왼쪽 무릎이 관찰되었습니다."), data.repAnalyses) }.isFailure)
        DirectionalClaimValidator.validate(listOf("2회차에서 왼쪽 무릎이 관찰되었습니다."), data.repAnalyses)
    }
}
