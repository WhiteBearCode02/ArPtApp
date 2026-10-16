package com.example.arptapp.presentation.report

import com.example.arptapp.domain.report.*
import com.example.arptapp.model.RepAnalysis
import org.junit.Assert.*
import org.junit.Test

class RepReportUiMapperTest {
    private fun rep(
        number: Int = 1,
        type: String = "SQUAT",
        score: Float? = 82f,
        joints: JointMetrics = JointMetrics(),
        asymmetry: AsymmetryMetrics = AsymmetryMetrics(),
        durations: PhaseDurations = PhaseDurations(),
        errors: List<FormErrorEvent> = emptyList(),
        strengths: List<String> = emptyList(),
        candidates: List<String> = emptyList(),
        priority: String = "",
        breakdown: RepScoreBreakdown? = null
    ) = StructuredRepAnalysis(
        repNumber = number, exerciseType = type, cameraView = "FRONT", totalDurationMs = null,
        phaseDurations = durations, jointMetrics = joints, asymmetryMetrics = asymmetry,
        errorEvents = errors, strengths = strengths, improvementCandidates = candidates,
        localPriorityFeedback = priority, score = score, observedFrames = 30, phaseModelVersion = 2,
        localScore = breakdown
    )

    private fun card(value: StructuredRepAnalysis) = RepReportUiMapper.map(emptyList(), listOf(value)).single()

    @Test fun `known strengths are shown only from saved evidence`() {
        val result = card(rep(strengths = listOf("KNEE_SYMMETRY_OBSERVED", "BOTH_PHASES_CAPTURED")))
        assertEquals(2, result.strengths.size)
        assertEquals(LocalRepFeedback.strength("KNEE_SYMMETRY_OBSERVED"), result.summary)
        assertTrue(result.improvements.isEmpty())
    }

    @Test fun `absence of errors never invents a strength`() {
        val result = card(rep())
        assertTrue(result.strengths.isEmpty())
        assertTrue(result.improvements.isEmpty())
        assertTrue(result.summary.contains("측정 가능한 항목"))
        assertFalse(result.summary.contains("문제 없음"))
    }

    @Test fun `unknown strength and improvement keys are hidden`() {
        val result = card(rep(strengths = listOf("FUTURE_STRENGTH"), candidates = listOf("UNREVIEWED_ISSUE")))
        assertTrue(result.strengths.isEmpty())
        assertTrue(result.improvements.isEmpty())
        assertFalse(result.toString().contains("FUTURE_STRENGTH"))
        assertFalse(result.toString().contains("UNREVIEWED_ISSUE"))
    }

    @Test fun `summary chooses improvement before a saved strength`() {
        val result = card(rep(strengths = listOf("KNEE_SYMMETRY_OBSERVED"),
            candidates = listOf("KNEE_VALGUS")))
        assertEquals(result.improvements.first(), result.summary)
        assertTrue(result.summary.contains("무릎이 안쪽"))
    }

    @Test fun `severity and occurrence order observations but never enter displayed copy`() {
        val result = card(rep(errors = listOf(
            FormErrorEvent(FormErrorType.KNEE_VALGUS, MovementPhase.DESCENT, BodySide.RIGHT, severity = 1, occurrenceFrames = 30),
            FormErrorEvent(FormErrorType.FORWARD_LEAN, MovementPhase.ASCENT, severity = 3, occurrenceFrames = 5)
        )))
        assertTrue(result.summary.startsWith("올라가는 구간에서 몸통"))
        assertTrue(result.improvements[1].contains("내려가는 구간에서 오른쪽 무릎"))
        assertFalse(result.toString().contains("severity"))
        assertFalse(result.toString().contains("프레임"))
        assertFalse(result.toString().contains("30"))
    }

    @Test fun `same severity prefers longer stored occurrence`() {
        val result = card(rep(errors = listOf(
            FormErrorEvent(FormErrorType.KNEE_VALGUS, side = BodySide.LEFT, severity = 2, occurrenceFrames = 3),
            FormErrorEvent(FormErrorType.KNEE_VALGUS, side = BodySide.RIGHT, severity = 2, occurrenceFrames = 9)
        )))
        assertTrue(result.summary.contains("오른쪽"))
    }

    @Test fun `all recorded phase labels and side labels are friendly Korean`() {
        val result = card(rep(errors = listOf(
            FormErrorEvent(FormErrorType.KNEE_VALGUS, MovementPhase.DESCENT, BodySide.LEFT),
            FormErrorEvent(FormErrorType.KNEE_VALGUS, MovementPhase.BOTTOM, BodySide.RIGHT),
            FormErrorEvent(FormErrorType.KNEE_VALGUS, MovementPhase.ASCENT)
        )))
        assertTrue(result.improvements.any { it.contains("내려가는 구간에서 왼쪽 무릎") })
        assertTrue(result.improvements.any { it.contains("하단 구간에서 오른쪽 무릎") })
        assertTrue(result.improvements.any { it.startsWith("올라가는 구간에서 무릎") })
        listOf("DESCENT", "BOTTOM", "ASCENT", "LEFT", "RIGHT", "KNEE_VALGUS").forEach {
            assertFalse(result.toString().contains(it))
        }
    }

    @Test fun `press bottom is transition not squat bottom`() {
        val result = card(rep(type = "SHOULDER_PRESS", errors = listOf(
            FormErrorEvent(FormErrorType.ELBOW_ASYMMETRY, MovementPhase.BOTTOM, BodySide.RIGHT))))
        assertTrue(result.improvements.single().startsWith("전환 구간에서 오른쪽 팔꿈치"))
    }

    @Test fun `duplicate event sentences and matching candidate types are not repeated`() {
        val error = FormErrorEvent(FormErrorType.KNEE_VALGUS, MovementPhase.BOTTOM, BodySide.LEFT)
        val result = card(rep(errors = listOf(error, error.copy(severity = 3)),
            candidates = listOf("KNEE_VALGUS", "FORWARD_LEAN", "FORWARD_LEAN")))
        assertEquals(2, result.improvements.size)
    }

    @Test fun `single saved priority is preserved and blank priority is absent`() {
        assertEquals("무릎 방향을 먼저 확인해 보세요.", card(rep(priority = "  무릎 방향을 먼저 확인해 보세요.  ")).priorityTip)
        assertNull(card(rep(priority = " \n ")).priorityTip)
    }

    @Test fun `unknown machine priority key is not exposed as coaching`() {
        val value = rep(priority = "UNVERIFIED_PRIORITY_KEY")
        assertNull(card(value).priorityTip)
        assertTrue(RepReportUiMapper.sessionSummary(session(listOf(value)), "").isEmpty())
    }

    @Test fun `legacy detail is retained without fabricated structured sections`() {
        val old = RepAnalysis(3, 77.25f, "저장된 각도 설명과 기존 코칭입니다.")
        val result = RepReportUiMapper.map(listOf(old), emptyList()).single()
        assertEquals(77.25f, result.score!!, 0f)
        assertEquals(old.detail, result.legacyDetail)
        assertTrue(result.strengths.isEmpty())
        assertTrue(result.improvements.isEmpty())
        assertTrue(result.metricGroups.isEmpty())
        assertNull(result.priorityTip)
        assertTrue(result.notes.single().contains("소급 분석하지 않았습니다"))
    }

    @Test fun `structured nullable score remains authoritative over legacy score`() {
        val result = RepReportUiMapper.map(listOf(RepAnalysis(1, 95f, "기존 설명")),
            listOf(rep(score = null, joints = JointMetrics(leftKneeMinAngle = 90.0), breakdown = emptyBreakdown()))).single()
        assertNull(result.score)
        assertNull(result.legacyDetail)
        assertTrue(result.status.contains("근거가 부족"))
    }

    @Test fun `identity only structured record preserves matching saved detail but not old score`() {
        val result = RepReportUiMapper.map(listOf(RepAnalysis(1, 95f, "기존 회차 설명")),
            listOf(rep(score = null))).single()
        assertNull(result.score)
        assertEquals("기존 회차 설명", result.legacyDetail)
        assertTrue(result.summary.contains("관찰 근거가 부족"))
        assertTrue(result.strengths.isEmpty())
        assertTrue(result.improvements.isEmpty())
    }

    @Test fun `unscored record without observations does not imply no improvement evidence`() {
        val result = card(rep(score = null))
        assertTrue(result.summary.contains("관찰 근거가 부족"))
        assertFalse(result.summary.contains("개선 후보가 관찰되지 않았습니다"))
        assertNull(result.legacyDetail)
    }

    @Test fun `structured score wins even without local breakdown`() {
        val result = RepReportUiMapper.map(listOf(RepAnalysis(1, 95f, "기존 설명")),
            listOf(rep(score = 72.3f))).single()
        assertEquals(72.3f, result.score!!, 0f)
    }

    @Test fun `structured only unscored rep is retained and merged list is sorted`() {
        val result = RepReportUiMapper.map(listOf(RepAnalysis(3, 70f, "저장 설명")),
            listOf(rep(number = 2, score = null), rep(number = 1)))
        assertEquals(listOf(1, 2, 3), result.map { it.repNumber })
        assertNull(result[1].score)
        assertNotNull(result[2].legacyDetail)
    }

    @Test fun `empty input and invalid rep identifiers do not create cards`() {
        assertTrue(RepReportUiMapper.map(emptyList(), emptyList()).isEmpty())
        assertTrue(RepReportUiMapper.map(listOf(RepAnalysis(0, 20f, "")), listOf(rep(number = -1))).isEmpty())
    }

    @Test fun `duplicate rep numbers produce one card and preserve first saved data`() {
        val result = RepReportUiMapper.map(listOf(RepAnalysis(1, 20f, "")), listOf(rep(score = 71f), rep(score = 99f)))
        assertEquals(1, result.size)
        assertEquals(71f, result.single().score!!, 0f)
    }

    @Test fun `nonfinite or out of range scores become absent not clamped or recomputed`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f, 101f).forEach {
            assertNull(card(rep(score = it)).score)
            assertNull(RepReportUiMapper.map(listOf(RepAnalysis(1, it, "")), emptyList()).single().score)
        }
        assertEquals(0f, card(rep(score = 0f)).score!!, 0f)
        assertEquals(100f, card(rep(score = 100f)).score!!, 0f)
    }

    @Test fun `status thresholds only express measurement comparison`() {
        assertTrue(card(rep(score = 90f)).status.contains("유사도"))
        assertTrue(card(rep(score = 70f)).status.contains("일부 측정"))
        assertTrue(card(rep(score = 69.9f)).status.contains("다시 확인"))
        listOf(0f, 70f, 100f).map { card(rep(score = it)).status }.forEach { text ->
            listOf("완벽", "정상", "비정상", "안전함", "부상").forEach { assertFalse(text.contains(it)) }
        }
    }

    @Test fun `squat contains only squat joint and phase groups`() {
        val result = card(rep(joints = JointMetrics(87.0, 91.0, 80.0, 85.0, 170.0, 175.0, 14.0),
            asymmetry = AsymmetryMetrics(6.0, 2.0, 9.0), durations = PhaseDurations(1100, 200, 900)))
        assertEquals(listOf("무릎", "고관절", "몸통", "동작 시간"), result.metricGroups.map { it.title })
        assertEquals(listOf("왼쪽 최소 각도", "오른쪽 최소 각도", "좌우 최대 차이"), result.metricGroups[0].rows.map { it.label })
        assertEquals("87.0°", result.metricGroups[0].rows[0].value)
        assertEquals("1.1초", result.metricGroups.last().rows.first().value)
        assertFalse(result.metricGroups.toString().contains("팔꿈치"))
        assertFalse(result.metricGroups.toString().contains("170.0"))
    }

    @Test fun `press contains elbow symmetry and transition groups without squat metrics`() {
        val result = card(rep(type = "SHOULDER_PRESS", joints = JointMetrics(80.0, 82.0, 90.0, 91.0, 170.0, 165.0, 20.0),
            asymmetry = AsymmetryMetrics(5.0, 4.0, 7.0), durations = PhaseDurations(1000, 200, 800)))
        assertEquals(listOf("팔꿈치", "좌우 균형", "동작 시간"), result.metricGroups.map { it.title })
        assertEquals("7.0°", result.metricGroups[1].rows.single().value)
        assertEquals("전환 구간", result.metricGroups.last().rows[1].label)
        assertFalse(result.metricGroups.toString().contains("무릎"))
        assertFalse(result.metricGroups.toString().contains("몸통"))
        assertFalse(result.metricGroups.toString().contains("하단"))
    }

    @Test fun `null metrics omit whole empty groups`() {
        assertTrue(card(rep()).metricGroups.isEmpty())
    }

    @Test fun `invalid finite bounds and nonfinite metrics never create rows`() {
        val result = card(rep(joints = JointMetrics(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 181.0,
            torsoLeanMaxDeg = Double.NEGATIVE_INFINITY), asymmetry = AsymmetryMetrics(-2.0),
            durations = PhaseDurations(-1L, null, null)))
        assertTrue(result.metricGroups.isEmpty())
        assertFalse(result.toString().contains("N/A"))
        assertFalse(result.toString().contains("NaN"))
        assertFalse(result.toString().contains("Infinity"))
    }

    @Test fun `genuine zero observed angle and duration remain visible`() {
        val result = card(rep(joints = JointMetrics(leftKneeMinAngle = 0.0), durations = PhaseDurations(bottomMs = 0L)))
        assertEquals("0.0°", result.metricGroups.first().rows.single().value)
        assertEquals("0.0초", result.metricGroups.last().rows.single().value)
    }

    @Test fun `unknown exercise does not borrow squat or press metrics`() {
        assertTrue(card(rep(type = "FUTURE_EXERCISE", joints = JointMetrics(leftKneeMinAngle = 90.0),
            durations = PhaseDurations(1000, 100, 1000))).metricGroups.isEmpty())
    }

    @Test fun `missing stored breakdown is not recalculated from valid joints`() {
        val result = card(rep(joints = JointMetrics(leftKneeMinAngle = 90.0)))
        assertFalse(result.metricGroups.any { it.title.contains("점수 구성") })
    }

    @Test fun `breakdown displays stored earned weight and never applies penalty again`() {
        val result = card(rep(breakdown = emptyBreakdown().copy(components = listOf(
            ComponentScore(RepScoreComponent.RANGE_OF_MOTION, 30f, 27.5f),
            ComponentScore(RepScoreComponent.ERROR_EVENTS, 10f, 7f)),
            penalties = listOf(IssuePenalty(FormErrorType.KNEE_VALGUS, 3f)))))
        val group = result.metricGroups.single()
        assertEquals("저장된 점수 구성", group.title)
        assertEquals("27.5 / 30.0", group.rows[0].value)
        assertEquals("7.0 / 10.0", group.rows[1].value)
        assertEquals(2, group.rows.size)
        assertFalse(group.toString().contains("-3"))
    }

    @Test fun `unavailable or malformed score components are hidden rather than scored zero`() {
        val result = card(rep(breakdown = emptyBreakdown().copy(components = listOf(
            ComponentScore(RepScoreComponent.RANGE_OF_MOTION, 30f, null),
            ComponentScore(RepScoreComponent.SYMMETRY, Float.NaN, 20f),
            ComponentScore(RepScoreComponent.STABILITY, 20f, Float.POSITIVE_INFINITY),
            ComponentScore(RepScoreComponent.PHASE_COMPLETENESS, 15f, 16f),
            ComponentScore(RepScoreComponent.ERROR_EVENTS, 0f, 0f)))))
        assertTrue(result.metricGroups.isEmpty())
    }

    @Test fun `valid zero earned component is not discarded`() {
        val result = card(rep(breakdown = emptyBreakdown().copy(components = listOf(
            ComponentScore(RepScoreComponent.ERROR_EVENTS, 10f, 0f)))))
        assertEquals("0.0 / 10.0", result.metricGroups.single().rows.single().value)
    }

    @Test fun `saved reference similarity is separate from score and explained`() {
        val result = card(rep(score = 72f).copy(referenceSimilarity = 95.5f))
        assertEquals(72f, result.score!!, 0f)
        assertEquals("95.5", result.metricGroups.single().rows.single().value)
        assertTrue(result.notes.any { it.contains("총점에 반영하지 않습니다") })
        assertTrue(card(rep().copy(referenceSimilarity = Float.NaN)).metricGroups.isEmpty())
    }

    @Test fun `partial capture and missing phase limitations remain explicit`() {
        val result = card(rep().copy(partialCapture = true))
        assertTrue(result.notes.any { it.contains("일부만 기록") })
        assertTrue(result.notes.any { it.contains("단계를 확정할 근거가 부족") })
    }

    @Test fun `legacy press phase note prevents retrospective reinterpretation`() {
        val result = card(rep(type = "SHOULDER_PRESS").copy(phaseModelVersion = 1))
        assertTrue(result.notes.any { it.contains("이전 버전의 단계 추정") })
        assertTrue(result.notes.any { it.contains("소급 분석하지 않았습니다") })
    }

    @Test fun `top complete press does not invent future return phase`() {
        val result = card(rep(type = "SHOULDER_PRESS", durations = PhaseDurations(ascentMs = 900)))
        assertTrue(result.notes.any { it.contains("이후 복귀 구간은 아직 측정되지 않았습니다") })
        assertEquals(listOf("올라가는 구간"), result.metricGroups.single().rows.map { it.label })
    }

    @Test fun `session legacy fallback preserves saved feedback and no empty placeholder`() {
        assertEquals(listOf("저장된 전체 피드백"), RepReportUiMapper.sessionSummary(null, "저장된 전체 피드백"))
        assertTrue(RepReportUiMapper.sessionSummary(null, "  ").isEmpty())
    }

    @Test fun `session summary uses stored dominant ordering and saved priority`() {
        val session = session(listOf(rep(priority = "무릎 방향을 먼저 확인해 보세요."))).copy(
            dominantIssues = listOf(FormErrorType.FORWARD_LEAN, FormErrorType.KNEE_VALGUS))
        val result = RepReportUiMapper.sessionSummary(session, "다른 기존 피드백")
        assertTrue(result[0].contains("몸통이 앞으로"))
        assertTrue(result[1].contains("무릎이 안쪽"))
        assertEquals("다음 운동에서 우선 확인: 무릎 방향을 먼저 확인해 보세요.", result[2])
        assertFalse(result.toString().contains("다른 기존 피드백"))
    }

    @Test fun `session can display saved events without aggregate dominant issues`() {
        val session = session(listOf(rep(errors = listOf(FormErrorEvent(FormErrorType.KNEE_VALGUS,
            MovementPhase.DESCENT, BodySide.RIGHT, severity = 2, occurrenceFrames = 9)))))
        val result = RepReportUiMapper.sessionSummary(session, "")
        assertTrue(result.single().contains("내려가는 구간에서 오른쪽 무릎"))
        assertFalse(result.single().contains("9"))
        assertFalse(result.single().contains("severity"))
    }

    @Test fun `session priority uses a saved dominant issue rep before generic first rep`() {
        val session = session(listOf(rep(priority = "회차가 기록되었습니다."),
            rep(number = 2, errors = listOf(FormErrorEvent(FormErrorType.KNEE_VALGUS)),
                priority = "오른쪽 무릎 방향을 먼저 확인해 보세요."))).copy(dominantIssues = listOf(FormErrorType.KNEE_VALGUS))
        val result = RepReportUiMapper.sessionSummary(session, "")
        assertEquals("다음 운동에서 우선 확인: 오른쪽 무릎 방향을 먼저 확인해 보세요.", result.last())
        assertFalse(result.toString().contains("회차가 기록되었습니다"))
    }

    @Test fun `session priority falls back to a saved event bearing rep before generic rep`() {
        val session = session(listOf(rep(priority = "회차가 기록되었습니다."),
            rep(number = 2, errors = listOf(FormErrorEvent(FormErrorType.FORWARD_LEAN)),
                priority = "몸통 기울기를 먼저 확인해 보세요.")))
        assertEquals("다음 운동에서 우선 확인: 몸통 기울기를 먼저 확인해 보세요.",
            RepReportUiMapper.sessionSummary(session, "").last())
    }

    @Test fun `session with no observations does not fabricate strengths or scores`() {
        assertTrue(RepReportUiMapper.sessionSummary(session(emptyList()), "").isEmpty())
        assertEquals(listOf("기존 피드백"), RepReportUiMapper.sessionSummary(session(emptyList()), "기존 피드백"))
    }

    @Test fun `mapping does not mutate stored analyses or recalculate breakdown`() {
        val value = rep(score = 12.3f, joints = JointMetrics(leftKneeMinAngle = 90.0),
            breakdown = emptyBreakdown().copy(normalizedScore = 99f))
        val original = value.copy()
        assertEquals(12.3f, card(value).score!!, 0f)
        assertEquals(original, value)
    }

    private fun emptyBreakdown() = RepScoreBreakdown(components = emptyList(), availableWeight = 0f,
        earned = 0f, normalizedScore = null)

    private fun session(reps: List<StructuredRepAnalysis>) = StructuredWorkoutAnalysis(
        exerciseType = "SQUAT", totalReps = reps.size, repAnalyses = reps)
}
