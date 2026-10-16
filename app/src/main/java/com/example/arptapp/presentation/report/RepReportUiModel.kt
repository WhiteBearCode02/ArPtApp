package com.example.arptapp.presentation.report

import com.example.arptapp.domain.report.BodySide
import com.example.arptapp.domain.report.FormErrorEvent
import com.example.arptapp.domain.report.FormErrorType
import com.example.arptapp.domain.report.LocalRepFeedback
import com.example.arptapp.domain.report.MovementPhase
import com.example.arptapp.domain.report.RepScoreComponent
import com.example.arptapp.domain.report.StructuredRepAnalysis
import com.example.arptapp.domain.report.StructuredWorkoutAnalysis
import com.example.arptapp.model.RepAnalysis
import java.util.Locale

/** Read-only view data shared by a completed workout and a saved history report. */
data class RepReportUiModel(
    val repNumber: Int,
    val score: Float?,
    val status: String,
    val summary: String,
    val strengths: List<String>,
    val improvements: List<String>,
    val priorityTip: String?,
    val metricGroups: List<MetricGroupUiModel>,
    val notes: List<String>,
    val legacyDetail: String?
)

data class MetricGroupUiModel(val title: String, val rows: List<MetricRowUiModel>)
data class MetricRowUiModel(val label: String, val value: String)

/** Each card expands independently; this contains no Android, persistence, or analysis behavior. */
class RepMetricExpansionState {
    private val expandedRepNumbers = mutableSetOf<Int>()

    fun isExpanded(repNumber: Int): Boolean = repNumber in expandedRepNumbers

    fun toggle(repNumber: Int): Boolean {
        if (!expandedRepNumbers.add(repNumber)) expandedRepNumbers.remove(repNumber)
        return isExpanded(repNumber)
    }
}

/**
 * UI-only interpretation of already saved observations. This mapper never runs a score calculator,
 * phase segmenter, error threshold, database write, or provider call. In particular a structured
 * null score stays null even when an older RepAnalysis happens to contain a score for the same rep.
 */
object RepReportUiMapper {
    private const val NO_IMPROVEMENT = "이번 회차에서 측정 가능한 항목에서는 별도 개선 후보가 관찰되지 않았습니다."
    private val strengthKeys = setOf("KNEE_SYMMETRY_OBSERVED", "ELBOW_SYMMETRY_OBSERVED", "BOTH_PHASES_CAPTURED")
    private val eventOrder = compareByDescending<FormErrorEvent> { it.severity }
        .thenByDescending { it.occurrenceFrames }
        .thenBy { it.type.ordinal }
        .thenBy { it.side?.ordinal ?: Int.MAX_VALUE }
        .thenBy { it.phase?.ordinal ?: Int.MAX_VALUE }

    fun map(analyses: List<RepAnalysis>, structured: List<StructuredRepAnalysis>): List<RepReportUiModel> {
        // First stored entry wins if damaged/legacy input contains duplicate repetition numbers.
        val legacyByNumber = analyses.filter { it.repNumber > 0 }.groupBy { it.repNumber }.mapValues { it.value.first() }
        val measuredByNumber = structured.filter { it.repNumber > 0 }.groupBy { it.repNumber }.mapValues { it.value.first() }
        return (legacyByNumber.keys + measuredByNumber.keys).sorted().map { number ->
            measuredByNumber[number]?.let { structuredCard(it, legacyByNumber[number]) }
                ?: legacyCard(legacyByNumber.getValue(number))
        }
    }

    /** Uses stored session issues, observations and coaching, never derives a new session score. */
    fun sessionSummary(structured: StructuredWorkoutAnalysis?, legacyFeedback: String): List<String> {
        if (structured == null) return listOfNotNull(savedText(legacyFeedback))
        val issues = structured.dominantIssues.distinct().map { issue ->
            "관찰된 개선 후보: ${issueObservation(FormErrorEvent(issue), structured.exerciseType)}"
        }
        val savedEvents = if (issues.isEmpty()) structured.repAnalyses
            .flatMap { it.errorEvents }
            .sortedWith(eventOrder)
            .map { issueObservation(it, structured.exerciseType) }
            .distinct() else emptyList()
        val dominant = structured.dominantIssues.firstOrNull()
        val priorityReps = structured.repAnalyses.filter { rep -> dominant != null &&
            (rep.errorEvents.any { it.type == dominant } || dominant.name in rep.improvementCandidates) } +
            structured.repAnalyses.filter { it.errorEvents.isNotEmpty() } + structured.repAnalyses
        val nextTip = priorityReps.asSequence()
            .mapNotNull { savedCoaching(it.localPriorityFeedback) }.firstOrNull()
            ?.let { "다음 운동에서 우선 확인: $it" }
        val stored = (issues + savedEvents + listOfNotNull(nextTip)).distinct()
        return stored.ifEmpty { listOfNotNull(savedText(legacyFeedback)) }
    }

    private fun structuredCard(rep: StructuredRepAnalysis, legacy: RepAnalysis?): RepReportUiModel {
        val score = validScore(rep.score)
        val strengths = rep.strengths.filter { it in strengthKeys }.distinct().map(LocalRepFeedback::strength)
        val events = rep.errorEvents.sortedWith(eventOrder)
        val eventTypes = events.map { it.type }.toSet()
        // New/unknown keys are intentionally hidden, not interpreted as evidence or shown raw.
        val candidateIssues = rep.improvementCandidates.mapNotNull { key ->
            FormErrorType.entries.firstOrNull { it.name == key }
        }.filter { it !in eventTypes }.distinct()
        val improvements = (events.map { issueObservation(it, rep.exerciseType) } +
            candidateIssues.map { issueObservation(FormErrorEvent(it), rep.exerciseType) }).distinct()
        val priority = savedCoaching(rep.localPriorityFeedback)
        val metrics = metricGroups(rep)
        val hasObservation = improvements.isNotEmpty() || strengths.isNotEmpty() || metrics.isNotEmpty()
        // An old record can contain the structured identity fields but no optional analysis facts.
        // Keep its saved prose available without allowing its old score to fill a structured null.
        val legacyDetail = if (!hasObservation && score == null && priority == null) legacy?.detail?.let(::savedText) else null
        return RepReportUiModel(
            repNumber = rep.repNumber,
            score = score,
            status = status(score),
            summary = improvements.firstOrNull() ?: strengths.firstOrNull() ?:
                if (!hasObservation && score == null) "이번 회차의 관찰 근거가 부족합니다. 촬영 조건과 저장된 설명을 확인해 주세요." else NO_IMPROVEMENT,
            strengths = strengths,
            improvements = improvements,
            priorityTip = priority,
            metricGroups = metrics,
            notes = notes(rep, score) + if (legacyDetail != null)
                listOf("구조화된 측정 근거가 부족하여 이전 기록에 저장된 설명을 함께 표시합니다.") else emptyList(),
            legacyDetail = legacyDetail
        )
    }

    private fun legacyCard(rep: RepAnalysis): RepReportUiModel = RepReportUiModel(
        repNumber = rep.repNumber,
        score = validScore(rep.score),
        status = status(validScore(rep.score)),
        summary = "이전 기록에 저장된 회차 설명을 확인해 주세요.",
        strengths = emptyList(),
        improvements = emptyList(),
        priorityTip = null,
        metricGroups = emptyList(),
        notes = listOf("이전 기록의 점수와 설명입니다. 현재 측정 방식으로 소급 분석하지 않았습니다."),
        legacyDetail = savedText(rep.detail)
    )

    private fun validScore(value: Float?): Float? = value?.takeIf { it.isFinite() && it in 0f..100f }

    private fun status(score: Float?): String = when {
        score == null -> "점수를 표시할 측정 근거가 부족합니다."
        score >= 90f -> "측정 가능한 기준과 높은 유사도를 보였습니다."
        score >= 70f -> "일부 측정 항목을 함께 확인해 보세요."
        else -> "여러 측정 항목에서 다시 확인할 부분이 있습니다."
    }

    private fun notes(rep: StructuredRepAnalysis, score: Float?): List<String> = buildList {
        if (score != null) add("초기 공학적 기준에 따른 비교 점수이며 의학적 진단이나 자세의 안전성을 보증하지 않습니다.")
        if (rep.partialCapture) add("관찰 구간 일부만 기록되었습니다.")
        if (rep.phaseModelVersion >= 2 && listOf(rep.phaseDurations.descentMs, rep.phaseDurations.bottomMs,
                rep.phaseDurations.ascentMs).none { it != null && it > 0L })
            add("동작 단계를 확정할 근거가 부족하여 단계별 관찰을 추측하지 않았습니다.")
        if (rep.exerciseType == "SHOULDER_PRESS" && rep.phaseModelVersion < 2)
            add("이전 버전의 단계 추정입니다. 현재 방식으로 소급 분석하지 않았습니다.")
        if (rep.exerciseType == "SHOULDER_PRESS" && rep.phaseModelVersion >= 2 &&
            rep.phaseDurations.descentMs == null && rep.phaseDurations.ascentMs?.let { it > 0L } == true)
            add("위로 올린 시점에 회차가 확정되어 이후 복귀 구간은 아직 측정되지 않았습니다.")
        if (rep.localScore != null) add("촬영 방향별 측정 항목이 달라 점수의 직접 비교에 한계가 있습니다.")
        if (validScore(rep.referenceSimilarity) != null) add("저장 기준 유사도는 총점에 반영하지 않습니다. 개인 기준은 올바른 자세를 인증하지 않습니다.")
    }

    private fun metricGroups(rep: StructuredRepAnalysis): List<MetricGroupUiModel> = buildList {
        fun group(title: String, rows: List<MetricRowUiModel?>) {
            rows.filterNotNull().takeIf { it.isNotEmpty() }?.let { add(MetricGroupUiModel(title, it)) }
        }
        with(rep.jointMetrics) {
            when (rep.exerciseType) {
                "SQUAT" -> {
                    group("무릎", listOf(angle("왼쪽 최소 각도", leftKneeMinAngle), angle("오른쪽 최소 각도", rightKneeMinAngle),
                        angle("좌우 최대 차이", rep.asymmetryMetrics.kneeAngleDifferenceDeg)))
                    group("고관절", listOf(angle("왼쪽 최소 각도", leftHipMinAngle), angle("오른쪽 최소 각도", rightHipMinAngle)))
                    group("몸통", listOf(angle("최대 기울기", torsoLeanMaxDeg)))
                }
                "SHOULDER_PRESS" -> {
                    group("팔꿈치", listOf(angle("왼쪽 최대 각도", leftElbowMaxAngle), angle("오른쪽 최대 각도", rightElbowMaxAngle)))
                    group("좌우 균형", listOf(angle("팔꿈치 최대 차이", rep.asymmetryMetrics.elbowAngleDifferenceDeg)))
                }
            }
        }
        if (rep.exerciseType in listOf("SQUAT", "SHOULDER_PRESS")) group("동작 시간", listOf(
            duration("내려가는 구간", rep.phaseDurations.descentMs),
            duration(if (rep.exerciseType == "SHOULDER_PRESS") "전환 구간" else "하단 구간", rep.phaseDurations.bottomMs),
            duration("올라가는 구간", rep.phaseDurations.ascentMs), duration("전체 관찰 시간", rep.totalDurationMs)))
        // Only saved component values are displayed. ERROR_EVENTS already includes any penalties;
        // displaying a second negative penalty row would imply a second deduction, so never do so.
        rep.localScore?.components?.distinctBy { it.component }?.mapNotNull { component ->
            val earned = component.earned?.takeIf { it.isFinite() && it >= 0f }
            val weight = component.weight.takeIf { it.isFinite() && it > 0f && it <= 100f }
            if (earned == null || weight == null || earned > weight) null else MetricRowUiModel(
                when (component.component) {
                    RepScoreComponent.RANGE_OF_MOTION -> "동작 범위"
                    RepScoreComponent.SYMMETRY -> "좌우 균형"
                    RepScoreComponent.STABILITY -> "몸통 안정성"
                    RepScoreComponent.PHASE_COMPLETENESS -> "관찰된 단계 완성도"
                    RepScoreComponent.ERROR_EVENTS -> "관찰 오류 반영"
                }, String.format(Locale.KOREA, "%.1f / %.1f", earned, weight))
        }?.takeIf { it.isNotEmpty() }?.let { add(MetricGroupUiModel("저장된 점수 구성", it)) }
        validScore(rep.referenceSimilarity)?.let {
            add(MetricGroupUiModel("내 기준 기록", listOf(MetricRowUiModel("저장 기준 유사도", String.format(Locale.KOREA, "%.1f", it)))))
        }
    }

    private fun angle(label: String, value: Double?): MetricRowUiModel? = value
        ?.takeIf { it.isFinite() && it in 0.0..180.0 }
        ?.let { MetricRowUiModel(label, String.format(Locale.KOREA, "%.1f°", it)) }

    private fun duration(label: String, value: Long?): MetricRowUiModel? = value
        ?.takeIf { it >= 0L }
        ?.let { MetricRowUiModel(label, String.format(Locale.KOREA, "%.1f초", it / 1000.0)) }

    /** All enum identifiers remain internal; severity/frame counts affect ordering, not wording. */
    private fun issueObservation(event: FormErrorEvent, exerciseType: String): String {
        val phase = when (event.phase) {
            MovementPhase.DESCENT -> "내려가는 구간에서 "
            MovementPhase.BOTTOM -> if (exerciseType == "SHOULDER_PRESS") "전환 구간에서 " else "하단 구간에서 "
            MovementPhase.ASCENT -> "올라가는 구간에서 "
            null -> ""
        }
        val side = when (event.side) { BodySide.LEFT -> "왼쪽 "; BodySide.RIGHT -> "오른쪽 "; null -> "" }
        val observation = when (event.type) {
            FormErrorType.KNEE_VALGUS -> "${side}무릎이 안쪽으로 이동하는 경향이 관찰되었습니다."
            FormErrorType.FORWARD_LEAN -> "${side}몸통이 앞으로 기우는 경향이 관찰되었습니다."
            FormErrorType.KNEE_ASYMMETRY -> if (side.isEmpty()) "좌우 무릎 각도 차이가 관찰되었습니다."
                else "${side}무릎과 반대쪽 무릎의 각도 차이가 관찰되었습니다."
            FormErrorType.HIP_ASYMMETRY -> if (side.isEmpty()) "좌우 고관절 각도 차이가 관찰되었습니다."
                else "${side}고관절과 반대쪽 고관절의 각도 차이가 관찰되었습니다."
            FormErrorType.ELBOW_ASYMMETRY -> if (side.isEmpty()) "좌우 팔꿈치 각도 차이가 관찰되었습니다."
                else "${side}팔꿈치와 반대쪽 팔꿈치의 각도 차이가 관찰되었습니다."
            FormErrorType.SHALLOW_DEPTH -> "${side}동작 깊이를 다시 확인할 후보가 관찰되었습니다."
            FormErrorType.INCOMPLETE_EXTENSION -> "${side}동작의 펴는 범위를 다시 확인할 후보가 관찰되었습니다."
        }
        return phase + observation
    }

    private fun savedText(value: String): String? = value.trim().takeIf { it.isNotEmpty() }

    /** Future machine identifiers are not a human coaching sentence. Legacy prose stays intact. */
    private fun savedCoaching(value: String): String? = savedText(value)?.takeUnless {
        it.matches(Regex("[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)+"))
    }
}
