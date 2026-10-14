package com.example.arptapp.presentation.report

import com.example.arptapp.domain.report.*
import java.util.Locale

/** Only observed facts; the legacy score remains an engineering comparison, not a diagnosis. */
object StructuredReportPresentation {
    fun detail(rep: StructuredRepAnalysis): String = buildString {
        append("로컬 측정 · ${when (rep.cameraView) { "FRONT" -> "정면"; "SIDE" -> "측면"; else -> "방향 미설정" }}\n")
        if (rep.partialCapture) append("관찰 구간 일부만 기록되었습니다.\n")
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
        ms("내려가기", rep.phaseDurations.descentMs); ms("하단 관찰 구간", rep.phaseDurations.bottomMs); ms("올라가기", rep.phaseDurations.ascentMs)
        append("\n관찰된 강점\n")
        append(rep.strengths.takeIf { it.isNotEmpty() }?.joinToString("\n") { LocalRepFeedback.strength(it) } ?: "강점을 판단할 측정 근거가 부족합니다.")
        if (rep.errorEvents.isNotEmpty()) {
            append("\n\n다음 회차에서 확인할 점\n")
            rep.errorEvents.forEach { e ->
                val phase = when (e.phase) { MovementPhase.DESCENT -> "내려가기"; MovementPhase.BOTTOM -> "하단 관찰 구간"; MovementPhase.ASCENT -> "올라가기"; null -> "구간 미확인" }
                append("$phase · ${LocalRepFeedback.forIssue(e.type)}\n")
            }
        }
        append("\n우선 코칭: ${rep.localPriorityFeedback}")
    }
}
