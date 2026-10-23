package com.example.arptapp.presentation.diagnostics

import com.example.arptapp.domain.detection.DetectorComparison

data class YoloLiveDiagnosticsUi(val status: String, val recognition: String, val performance: String)

/** Converts observations to explanation only. The original formatter/log and all detectors stay intact. */
object YoloLiveDiagnosticsPresentation {
    fun present(data: DetectorComparison): YoloLiveDiagnosticsUi {
        val result = data.yolo.result
        val hasPrediction = result.predictionAllowed && result.timings != null
        val status = when {
            !result.predictionAllowed -> YoloDiagnosticGuide.status(result.status.name) + "\nYOLO 실행은 비활성입니다. 관절 기반 운동 판정과는 별개입니다."
            result.timings == null -> "모델 준비 완료 · 첫 분석 결과를 기다려요"
            else -> "YOLO 분석 결과를 받았어요 · 시험 인식 중"
        }
        val recognition = buildString {
            append("이번 화면: ${if (hasPrediction) YoloDiagnosticGuide.exercise(result.exerciseType.name) else "분석 결과 대기"}")
            append("\n모델 확신도: ${YoloDiagnosticGuide.confidence(if (hasPrediction) result.selected?.confidence?.toDouble() else null)}")
            append("\n연속 화면의 판단: ${YoloDiagnosticGuide.exercise(if (hasPrediction) data.yolo.stableExercise.name else "UNKNOWN")}")
            append("\n관절 기반 감지: ${YoloDiagnosticGuide.exercise(data.fallbackDetected.name)}")
            append("\n기록 중인 운동: ${YoloDiagnosticGuide.exercise(data.sessionExerciseType.name)}")
            append("\n촬영 방향: ${YoloDiagnosticGuide.direction(data.cameraView.name)}")
            if (data.posePeopleCount > 1 || result.detectionCount > 1)
                append("\n주의: 여러 검출이 있어 YOLO 결과와 분석 중인 사람을 같은 대상으로 단정할 수 없어요.")
        }
        val timing = result.timings
        val performance = buildString {
            append("YOLO 처리 속도: ")
            append(data.processedFps?.takeIf { it.isFinite() && it >= 0 }?.let { "초당 ${YoloDiagnosticGuide.decimal(it, 2)}장" } ?: "세션 측정 전")
            append("\n한 화면 처리 시간: ${timing?.let { "${it.totalMs} ms" } ?: "미측정"}")
            append("\n기기 발열 상태: ${YoloDiagnosticGuide.thermal(data.thermalStatus)}")
        }
        return YoloLiveDiagnosticsUi(status, recognition, performance)
    }
}
