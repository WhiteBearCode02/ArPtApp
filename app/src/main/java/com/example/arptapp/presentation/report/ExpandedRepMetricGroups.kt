package com.example.arptapp.presentation.report

import com.example.arptapp.domain.report.*
import java.util.Locale

/** Presentation of saved V2 facts only. No scoring/re-analysis or null-to-zero conversions. */
internal object ExpandedRepMetricGroups {
    private const val NOT_EVALUATED = "측정 불가"
    fun create(rep: StructuredRepAnalysis): List<MetricGroupUiModel> {
        fun angle(name: String, value: Double?) = MetricRowUiModel(name,
            value?.takeIf { it.isFinite() && it in 0.0..180.0 }?.let { String.format(Locale.KOREA, "%.1f°", it) } ?: "측정 불가")
        val j = rep.jointMetrics
        val elbows = MetricGroupUiModel("팔꿈치 동작 범위", listOf(
            angle("왼쪽 최소 각도", j.leftElbowMinAngle), angle("왼쪽 최대 각도", j.leftElbowMaxAngle),
            angle("왼쪽 관찰 ROM", j.leftElbowRomDeg), angle("오른쪽 최소 각도", j.rightElbowMinAngle),
            angle("오른쪽 최대 각도", j.rightElbowMaxAngle), angle("오른쪽 관찰 ROM", j.rightElbowRomDeg)))
        val upper = MetricGroupUiModel("상완 위치 변화", listOf(angle("왼쪽 변화량", j.upperArmDriftLeftDeg), angle("오른쪽 변화량", j.upperArmDriftRightDeg)))
        val groups = when (rep.exerciseType) {
            "BICEP_CURL" -> listOf(MetricGroupUiModel("사용 팔", listOf(MetricRowUiModel("해부학적 좌우",
                rep.activeSide?.let(LocalEventPresentation::side) ?: "측정 불가"))), elbows, upper,
                MetricGroupUiModel("몸통·반대 팔 비교", listOf(angle("몸통 기울기 변화", j.torsoInclinationChangeDeg),
                    angle("직전 반대 팔 ROM 차이", rep.asymmetryMetrics.contralateralRomDifferenceDeg))))
            "PUSH_UP" -> listOf(elbows, MetricGroupUiModel("좌우 균형·몸통 정렬", listOf(
                angle("팔꿈치 최대 차이", rep.asymmetryMetrics.elbowAngleDifferenceDeg),
                angle("정면 어깨 높이 차이 proxy", j.shoulderHeightDifferenceDeg), angle("측면 몸통 정렬 변화 proxy", j.bodyLineDeviationDeg))))
            "BENT_OVER_ROW" -> listOf(elbows, MetricGroupUiModel("몸통 기울기", listOf(
                angle("측면 최대 기울기", j.torsoInclinationDeg), angle("기울기 변화량", j.torsoInclinationChangeDeg))),
                MetricGroupUiModel("좌우 균형", listOf(angle("팔꿈치 최대 차이", rep.asymmetryMetrics.elbowAngleDifferenceDeg))))
            "TRICEPS_EXTENSION" -> listOf(elbows, upper, MetricGroupUiModel("몸통·좌우 균형", listOf(
                angle("몸통 기울기 변화", j.torsoInclinationChangeDeg), angle("팔꿈치 최대 차이", rep.asymmetryMetrics.elbowAngleDifferenceDeg))))
            "SIDE_BEND" -> listOf(MetricGroupUiModel("움직인 방향", listOf(MetricRowUiModel("해부학적 좌우",
                rep.activeSide?.let(LocalEventPresentation::side) ?: "측정 불가"))), MetricGroupUiModel("몸통 측굴 관찰", listOf(
                angle("최대 기울기", j.peakLateralFlexionDeg), angle("중앙 복귀와 시작 위치 차이", j.returnToNeutralDeviationDeg),
                angle("직전 반대 방향 범위 차이", rep.asymmetryMetrics.lateralRangeDifferenceDeg))))
            else -> return emptyList()
        }
        fun duration(label: String, value: Long?) = MetricRowUiModel(label,
            value?.takeIf { it >= 0 }?.let { String.format(Locale.KOREA, "%.1f초", it / 1000.0) } ?: "측정 불가")
        return (groups + MetricGroupUiModel("동작 시간", listOf(
            duration(ExerciseReportLanguage.phase(rep.exerciseType, MovementPhase.DESCENT), rep.phaseDurations.descentMs),
            duration(ExerciseReportLanguage.phase(rep.exerciseType, MovementPhase.BOTTOM), rep.phaseDurations.bottomMs),
            duration(ExerciseReportLanguage.phase(rep.exerciseType, MovementPhase.ASCENT), rep.phaseDurations.ascentMs),
            duration("전체 관찰 시간", rep.totalDurationMs)))).mapNotNull { group ->
            group.copy(rows = group.rows.filter { it.value != NOT_EVALUATED }).takeIf { it.rows.isNotEmpty() }
        }
    }
}
