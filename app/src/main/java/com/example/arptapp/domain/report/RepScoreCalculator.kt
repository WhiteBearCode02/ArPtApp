package com.example.arptapp.domain.report

import kotlinx.serialization.Serializable
import kotlin.math.round
import java.util.Locale

@Serializable enum class RepScoreComponent { RANGE_OF_MOTION, SYMMETRY, STABILITY, PHASE_COMPLETENESS, ERROR_EVENTS }
@Serializable data class ComponentScore(val component: RepScoreComponent, val weight: Float, val earned: Float?)
@Serializable data class IssuePenalty(val issue: FormErrorType, val points: Float)
@Serializable data class RepScoreBreakdown(
    val policyVersion: Int = 1, val source: String = "ENGINEERING_INITIAL",
    val components: List<ComponentScore>, val availableWeight: Float, val earned: Float,
    val normalizedScore: Float?, val penalties: List<IssuePenalty> = emptyList()
)

/** Unvalidated engineering comparison, not a medical target, safe ROM prescription or perfect-form grade. */
object RepScorePolicy {
    const val ROM_WEIGHT = 30f
    const val SYMMETRY_WEIGHT = 25f
    const val STABILITY_WEIGHT = 20f
    const val PHASE_WEIGHT = 15f
    const val ERROR_WEIGHT = 10f
    const val SQUAT_REFERENCE_INTERIOR_DEG = 90.0
    const val PRESS_REFERENCE_EXTENSION_DEG = 170.0
    const val PRESS_REFERENCE_BOTTOM_DEG = 70.0
    const val SYMMETRY_FULL_LOSS_DEG = 40.0
    const val LEAN_TOLERANCE_DEG = 10.0
    const val LEAN_FULL_LOSS_DEG = 50.0
    const val ISSUE_PENALTY_CAP = 3f
}

/** Structured measurements first, local score second. DTW/referenceSimilarity never enters this formula. */
object RepScoreCalculator {
    fun calculate(rep: StructuredRepAnalysis): RepScoreBreakdown {
        val supported = rep.exerciseType in listOf("SQUAT", "SHOULDER_PRESS") && rep.cameraView in listOf("FRONT", "SIDE") && rep.observedFrames >= 3
        fun angle(v: Double?) = v?.takeIf { it.isFinite() && it in 0.0..180.0 }
        val squat = rep.exerciseType == "SQUAT"
        val jointAngles = with(rep.jointMetrics) {
            if (squat) listOfNotNull(angle(leftKneeMinAngle), angle(rightKneeMinAngle))
            else listOfNotNull(angle(leftElbowMaxAngle), angle(rightElbowMaxAngle))
        }
        val rom = if (supported && jointAngles.isNotEmpty()) {
            val a = jointAngles.average()
            if (squat) (180.0 - a) / (180.0 - RepScorePolicy.SQUAT_REFERENCE_INTERIOR_DEG)
            else (a - RepScorePolicy.PRESS_REFERENCE_BOTTOM_DEG) /
                (RepScorePolicy.PRESS_REFERENCE_EXTENSION_DEG - RepScorePolicy.PRESS_REFERENCE_BOTTOM_DEG)
        } else null
        val difference = if (squat) rep.asymmetryMetrics.kneeAngleDifferenceDeg else rep.asymmetryMetrics.elbowAngleDifferenceDeg
        val symmetry = if (supported && rep.cameraView == "FRONT") angle(difference)?.let {
            1.0 - it / RepScorePolicy.SYMMETRY_FULL_LOSS_DEG
        } else null
        val stability = if (supported && squat && rep.cameraView == "SIDE") angle(rep.jointMetrics.torsoLeanMaxDeg)?.let {
            1.0 - (it - RepScorePolicy.LEAN_TOLERANCE_DEG).coerceAtLeast(0.0) /
                (RepScorePolicy.LEAN_FULL_LOSS_DEG - RepScorePolicy.LEAN_TOLERANCE_DEG)
        } else null
        // The live press counter completes at TOP: a future return/descent is NOT a missing phase error.
        val phase = if (supported && rep.phaseModelVersion >= 2 && !rep.partialCapture &&
            rep.phaseDurations.ascentMs?.let { it > 0 } == true &&
            (!squat || rep.phaseDurations.descentMs?.let { it > 0 } == true)) 1.0 else null
        val measurable = rom != null || symmetry != null || stability != null
        val penalties = if (supported && measurable) rep.errorEvents
            .filter { it.severity in 1..3 && it.occurrenceFrames >= 3 }
            .groupBy { it.type }.map { (type, events) ->
                // Same issue repeated across phases/sides cannot cause unbounded deductions.
                val frames = events.groupBy { it.side to it.phase }.values
                    .sumOf { group -> group.maxOf { it.occurrenceFrames } }.coerceAtMost(rep.observedFrames)
                val exposure = (frames.toDouble() / rep.observedFrames * 2).coerceIn(0.0, 1.0)
                IssuePenalty(type, (events.maxOf { it.severity } / 3f * exposure.toFloat() * RepScorePolicy.ISSUE_PENALTY_CAP)
                    .coerceIn(0f, RepScorePolicy.ISSUE_PENALTY_CAP))
            } else emptyList()
        var remainingPenalty = RepScorePolicy.ERROR_WEIGHT
        val appliedPenalties = penalties.sortedBy { it.issue.ordinal }.map { p ->
            val applied = minOf(p.points, remainingPenalty)
            remainingPenalty -= applied
            p.copy(points = applied)
        }.filter { it.points > 0f }
        val eventEarned = if (supported && measurable) remainingPenalty else null
        fun component(id: RepScoreComponent, weight: Float, ratio: Double?) = ComponentScore(id, weight,
            ratio?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0)?.let { (it * weight).toFloat() })
        val parts = listOf(
            component(RepScoreComponent.RANGE_OF_MOTION, RepScorePolicy.ROM_WEIGHT, rom),
            component(RepScoreComponent.SYMMETRY, RepScorePolicy.SYMMETRY_WEIGHT, symmetry),
            component(RepScoreComponent.STABILITY, RepScorePolicy.STABILITY_WEIGHT, stability),
            component(RepScoreComponent.PHASE_COMPLETENESS, RepScorePolicy.PHASE_WEIGHT, phase),
            ComponentScore(RepScoreComponent.ERROR_EVENTS, RepScorePolicy.ERROR_WEIGHT, eventEarned))
        val denominator = parts.filter { it.earned != null }.sumOf { it.weight.toDouble() }.toFloat()
        val earned = parts.sumOf { it.earned?.toDouble() ?: 0.0 }.toFloat()
        return RepScoreBreakdown(components = parts, availableWeight = denominator, earned = earned,
            normalizedScore = if (denominator > 0 && rom != null) (round(earned / denominator * 1000) / 10).coerceIn(0f, 100f) else null,
            penalties = appliedPenalties)
    }
    fun score(rep: StructuredRepAnalysis, referenceSimilarity: Float? = null): StructuredRepAnalysis {
        val breakdown = calculate(rep)
        return rep.copy(score = breakdown.normalizedScore, localScore = breakdown,
            referenceSimilarity = referenceSimilarity?.takeIf { it.isFinite() && it in 0f..100f })
    }
}

object RepScoreDescription {
    fun format(rep: StructuredRepAnalysis): String = buildString {
        val score = rep.localScore ?: return@buildString
        append("점수 구성 · ENGINEERING_INITIAL v${score.policyVersion}\n")
        score.components.forEach { c ->
            val name = when (c.component) {
                RepScoreComponent.RANGE_OF_MOTION -> "동작 범위"
                RepScoreComponent.SYMMETRY -> "좌우 균형"
                RepScoreComponent.STABILITY -> "몸통 안정성"
                RepScoreComponent.PHASE_COMPLETENESS -> "관찰된 단계 완성도"
                RepScoreComponent.ERROR_EVENTS -> "관찰 오류 반영"
            }
            append(if (c.earned == null) "$name: 측정 불가 · 총점에서 제외\n" else
                String.format(Locale.KOREA, "%s: %.1f/%.0f\n", name, c.earned, c.weight))
        }
        append(String.format(Locale.KOREA, "측정 가능한 가중치 %.0f/100 · 획득 %.1f점으로 재정규화\n", score.availableWeight, score.earned))
        if (score.penalties.isNotEmpty()) append(String.format(Locale.KOREA, "관찰 오류 감점 합계: -%.1f (항목당 최대 %.0f)\n",
            score.penalties.sumOf { it.points.toDouble() }, RepScorePolicy.ISSUE_PENALTY_CAP))
        rep.referenceSimilarity?.let { append(String.format(Locale.KOREA, "저장 기준 유사도: %.1f · 총점에 반영하지 않음\n", it)) }
        append("촬영 방향별 측정 항목이 달라 직접 비교에 한계가 있습니다. 의학적 진단이나 안전한 자세를 보증하는 점수가 아닙니다.\n")
    }
}
