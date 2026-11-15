package com.example.arptapp.presentation.diagnostics

import com.example.arptapp.data.model.StandardPose
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.analyzer.ExerciseMotionPolicy
import java.util.Locale
import kotlin.math.abs

/** Ephemeral scalars only. Never serialized, uploaded, or used to change session routing.
 * Caller must enforce BuildConfig.DEBUG and throttle before formatting/logging.
 */
internal data class MeasurementDiagnosticSnapshot(
    val selected: String, val detected: String, val view: CameraView, val evaluable: Boolean,
    val representative: Double?, val invalidDurationMs: Long, val count: Int, val phase: String,
    val reason: String, val minimumVisibility: Float?, val leftElbow: Double? = null,
    val rightElbow: Double? = null, val torsoInclination: Double? = null
)

internal object MeasurementDiagnosticFormatter {
    private fun degree(value: Double?) = value?.takeIf(Double::isFinite)?.let {
        String.format(Locale.ROOT, "%.1f°", it)
    } ?: "미측정"

    fun format(s: MeasurementDiagnosticSnapshot): String = buildString {
        appendLine("횟수 측정 진단 · DEBUG")
        appendLine("선택/session: ${s.selected} · 자동 감지(진단만): ${s.detected}")
        appendLine("촬영 방향: ${s.view} · 측정 가능: ${if (s.evaluable) "YES" else "NO"}")
        appendLine("누락 시간: ${s.invalidDurationMs} ms / ${ExerciseMotionPolicy.MAX_FRAME_GAP_MS} ms · 횟수: ${s.count}")
        appendLine("상태: ${s.phase} · 사유: ${s.reason}")
        appendLine("관련 관절 최소 visibility: ${s.minimumVisibility?.takeIf(Float::isFinite)?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "미측정"} (기준 ${ExerciseMotionPolicy.VISIBILITY})")
        when (s.selected) {
            "SQUAT" -> appendLine("무릎 대표: ${degree(s.representative)} · 내려감 ≤${StandardPose.SQUAT_DOWN_THRESHOLD}° / 올라옴 ≥${StandardPose.SQUAT_UP_THRESHOLD}°")
            "SHOULDER_PRESS" -> appendLine("팔꿈치 대표: ${degree(s.representative)} · 아래 ≤${StandardPose.SHOULDER_BOTTOM_THRESHOLD}° / 위 ≥${StandardPose.SHOULDER_TOP_THRESHOLD}°")
            "SIDE_BEND" -> {
                appendLine("기울기(부호): ${degree(s.representative)} · 절대값: ${degree(s.representative?.let(::abs))}")
                appendLine("기울임 ≥${ExerciseMotionPolicy.sideBend.turnDeg}° / 중립 복귀 ≤${ExerciseMotionPolicy.sideBend.returnDeg}°")
            }
            else -> {
                appendLine("팔꿈치 대표: ${degree(s.representative)} · 왼쪽: ${degree(s.leftElbow)} / 오른쪽: ${degree(s.rightElbow)}")
                ExerciseMotionPolicy.forExercise(s.selected)?.let {
                    appendLine(if (s.selected == "TRICEPS_EXTENSION") "굽힘 ≤${it.turnDeg}° / 폄 ≥${it.returnDeg}°"
                        else "전환 ≤${it.turnDeg}° / 복귀 ≥${it.returnDeg}°")
                }
                if (s.selected == "BENT_OVER_ROW") appendLine("몸통 기울기: ${degree(s.torsoInclination)}")
            }
        }
        append("WAIT_BASELINE: 시작 자세 대기 · ARMED: 준비됨 · MOVING: 이동 중 · TURNED: 목표 도달 후 복귀 대기\n짧은 관절 누락은 회차 보존, 1초 이상 누락은 미완성 회차만 초기화합니다. 각도 기준은 초기 공학 기준입니다.")
    }
}
