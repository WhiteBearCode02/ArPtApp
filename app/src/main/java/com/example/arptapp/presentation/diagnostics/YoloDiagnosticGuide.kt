package com.example.arptapp.presentation.diagnostics

import java.util.Locale

/** Read-only wording shared by live/saved diagnostics. Never promotes a model, tunes thresholds,
 * assigns a score, or treats missing samples as zero. Unknown/new codes stay explicitly unknown.
 */
object YoloDiagnosticGuide {
    const val ROLE = "YOLO는 운동 종류를 시험 인식하는 보조 AI입니다. 현재 운동 점수와 자세 분석 판정에는 사용되지 않습니다. 실제 운동 판정은 관절 분석이 담당합니다."
    fun exercise(value: String): String = when (value) {
        "SQUAT" -> "스쿼트"; "SHOULDER_PRESS" -> "숄더 프레스"; else -> "아직 판단하지 못함"
    }
    fun direction(value: String): String = when (value) {
        "FRONT" -> "정면"; "SIDE" -> "측면"; else -> "선택 전"
    }
    fun status(value: String): String = when (value) {
        "INITIALIZING" -> "모델을 준비하고 있어요"
        "READY" -> "모델 준비 완료"
        "CONTRACT_NOT_VERIFIED" -> "실행 규칙 검증 전 · YOLO 분석 중지"
        "MODEL_MISSING" -> "휴대폰에 검증용 모델이 없어요"
        "MODEL_IDENTITY_MISMATCH" -> "허용된 모델 파일과 달라 실행을 막았어요"
        "TENSOR_MISMATCH" -> "모델의 입력·출력 규격이 맞지 않아요"
        "UNSUPPORTED_RUNTIME" -> "현재 실행 환경이 이 모델을 지원하지 않아요"
        "INITIALIZATION_FAILED" -> "모델 준비에 실패했어요"
        "INFERENCE_FAILED" -> "AI 분석 중 오류가 발생했어요"
        "INVALID_OUTPUT" -> "모델 결과 형식이 올바르지 않아요"
        "TIMED_OUT" -> "응답이 지연되어 YOLO 분석을 중지했어요"
        "CLOSED" -> "YOLO 분석이 종료되었어요"
        else -> "상태를 확인할 수 없어요 · 기술 정보 확인"
    }
    fun thermal(value: Int?): String = when (value) {
        0 -> "발열 제한 없음"; 1 -> "가벼운 발열 제한"; 2 -> "보통 발열 제한"
        3 -> "심한 발열 제한"; 4 -> "위험 수준의 발열 제한"
        5 -> "긴급 수준의 발열 제한"; 6 -> "기기 종료 수준의 발열"
        else -> "확인할 수 없음"
    }
    fun decimal(value: Double?, places: Int = 1): String = value?.takeIf { it.isFinite() && it >= 0 }
        ?.let { String.format(Locale.KOREA, "%.${places}f", it) } ?: "미측정"
    fun confidence(value: Double?): String = value?.takeIf { it.isFinite() && it in 0.0..1.0 }
        ?.let { decimal(it * 100) + "%" } ?: "미측정"
}
