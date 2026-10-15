package com.example.arptapp.presentation.report

import com.example.arptapp.domain.report.*
import java.util.Locale

/** Only observed facts; the legacy score remains an engineering comparison, not a diagnosis. */
object StructuredReportPresentation {
    fun detail(rep: StructuredRepAnalysis): String = buildString {
        append("로컬 측정 · ${when (rep.cameraView) { "FRONT" -> "정면"; "SIDE" -> "측면"; else -> "방향 미설정" }}\n")
        if (rep.localScore != null) append(RepScoreDescription.format(rep)).append('\n')
        if (rep.partialCapture) append("관찰 구간 일부만 기록되었습니다.\n")
        if (rep.phaseModelVersion >= 2 && rep.phaseDurations == PhaseDurations())
            append("동작 단계를 확정할 근거가 부족하여 단계별 오류를 추측하지 않았습니다.\n")
        if (rep.exerciseType == "SHOULDER_PRESS" && rep.phaseModelVersion < 2)
            append("이전 버전의 단계 추정입니다. 현재 방식으로 소급 분석하지 않았습니다.\n")
        fun degrees(name: String, value: Double?) { if (value != null) append(String.format(Locale.KOREA, "%s %.1f°\n", name, value)) }
        fun ms(name: String, value: Long?) { append("$name: ${value?.let { "${it}ms" } ?: "측정 불가"}\n") }
        with(rep.jointMetrics) {
            degrees("왼쪽 무릎 최소 내각", leftKneeMinAngle); degrees("오른쪽 무릎 최소 내각", rightKneeMinAngle)
            degrees("왼쪽 고관절 최소 내각", leftHipMinAngle); degrees("오른쪽 고관절 최소 내각", rightHipMinAngle)
            degrees("왼쪽 팔꿈치 최대 내각", leftElbowMaxAngle); degrees("오른쪽 팔꿈치 최대 내각", rightElbowMaxAngle)
            degrees("몸통 최대 기울기", torsoLeanMaxDeg)
        }
        degrees("좌우 무릎 최대 차이", rep.asymmetryMetrics.kneeAngleDifferenceDeg)
        degrees("좌우 팔꿈치 최대 차이", rep.asymmetryMetrics.elbowAngleDifferenceDeg)
        ms("내려가는 구간", rep.phaseDurations.descentMs); ms("하단 구간", rep.phaseDurations.bottomMs); ms("올라가는 구간", rep.phaseDurations.ascentMs)
        if (rep.exerciseType == "SHOULDER_PRESS" && rep.phaseModelVersion >= 2 && rep.phaseDurations.descentMs == null && rep.phaseDurations.ascentMs != null)
            append("위로 올린 시점에 회차가 확정되어 이후 복귀 구간은 아직 측정되지 않았습니다.\n")
        append("\n관찰된 강점\n")
        append(rep.strengths.takeIf { it.isNotEmpty() }?.joinToString("\n") { LocalRepFeedback.strength(it) } ?: "강점을 판단할 측정 근거가 부족합니다.")
        if (rep.errorEvents.isNotEmpty()) {
            append("\n\n다음 회차에서 확인할 점\n")
            rep.errorEvents.forEach { e ->
                append("• ${LocalEventPresentation.observation(e)}\n")
            }
        }
        if (rep.errorEvents.isEmpty()) append("\n\n현재 측정 가능한 항목에서 별도 개선 후보가 관찰되지 않았습니다.\n")
        append("\n우선 코칭: ${rep.localPriorityFeedback}")
    }
}
