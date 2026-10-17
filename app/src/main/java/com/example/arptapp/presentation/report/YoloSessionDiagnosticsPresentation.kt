package com.example.arptapp.presentation.report

import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.domain.detection.YoloSessionDiagnostics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local developer comparison only. This mapper cannot calculate posture scores or call a server. */
object YoloSessionDiagnosticsPresentation {
    fun visibleDiagnostics(debug: Boolean, json: String?): YoloSessionDiagnostics? =
        if (debug) YoloSessionDiagnosticsCodec.decode(json) else null

    fun groups(value: YoloSessionDiagnostics): List<MetricGroupUiModel> = listOf(
        MetricGroupUiModel("모델·세션", listOf(
            row("모델", value.modelId),
            row("모델 기준 SHA256 · 실제 로드 증명 아님", value.modelSha256.ifBlank { "정보 없음" }),
            row("모델 상태", value.modelStatus),
            row("Export 계약", value.contractStatus),
            row("Shadow Runtime", when (value.shadowRuntimeEnabled) {
                true -> "ENABLED"; false -> "DISABLED"; null -> "기록 없음 · 이전 버전"
            }),
            row("Primary", "LANDMARK · YOLO는 판정에 사용하지 않음"),
            row("Prediction", if (value.predictionEnabled) "활성" else "비활성 · 실제 prediction 표본이 없을 수 있습니다"),
            row("세션 운동", exercise(value.expectedSessionExercise)),
            row("촬영 방향", when (value.cameraView) { "FRONT" -> "정면"; "SIDE" -> "측면"; else -> "UNKNOWN" }),
            row("시작", timestamp(value.startedAt)), row("종료", timestamp(value.endedAt))
        )),
        MetricGroupUiModel("처리 프레임", listOf(
            row("Shadow 입력 후보 프레임", value.totalFramesSeen.toString()),
            row("Gate 허용 프레임", value.admittedFrames.toString()),
            row("실제 처리 프레임", value.processedFrames.toString()),
            row("프레임 기준", "후보는 기존 카메라 분석 throttle을 통과한 Shadow 입력만 포함 · 원본 카메라 전체 프레임 수가 아님")
        )),
        detections("YOLO raw 결과", value.rawDetectionCounts, value.processedFrames),
        detections("YOLO stable 결과", value.stableDetectionCounts, value.processedFrames),
        MetricGroupUiModel("실제 detection confidence", if (value.confidenceSampleCount > 0L) listOfNotNull(
            row("Detection 표본 수", value.confidenceSampleCount.toString()),
            value.averageConfidence?.let { row("평균 confidence", decimal(it)) },
            value.minConfidence?.let { row("최소 confidence", decimal(it)) },
            value.maxConfidence?.let { row("최대 confidence", decimal(it)) },
            row("평균의 분모", "실제 detection이 있는 프레임만 포함 · UNKNOWN을 0으로 합산하지 않음")
        ) else listOf(row("Confidence", "실제 detection 표본 없음 · 0으로 대체하지 않음"))),
        MetricGroupUiModel("처리 성능", listOfNotNull(
            value.averageProcessedFps?.let { row("평균 처리 FPS", decimal(it, 2) + " FPS") },
            value.averageProcessedFps?.let { row("FPS 기준", "완료된 native inference / 활성 세션 시간 · CameraX·MediaPipe FPS가 아님") },
            value.averagePreprocessMs?.let { row("평균 preprocessing", decimal(it, 2) + " ms") },
            value.averageInferenceMs?.let { row("평균 inference", decimal(it, 2) + " ms") },
            value.averagePostprocessMs?.let { row("평균 postprocessing", decimal(it, 2) + " ms") },
            value.averageTotalMs?.let { row("평균 total latency", decimal(it, 2) + " ms") },
            value.maxThermalStatus?.let { row("최대 thermal 단계", it.toString()) }
        ).ifEmpty { listOf(row("성능", "미측정")) }),
        MetricGroupUiModel("확정된 세션 운동과의 비교", listOf(
            row("일치", value.matchingStableFrames.toString()),
            row("불일치", value.mismatchingStableFrames.toString()),
            row("UNKNOWN", value.unknownStableFrames.toString()),
            row("세션 운동 미확정으로 비교 제외", value.unknownSessionFrames.toString()),
            row("Known-match rate", value.knownMatchRate?.let { decimal(it * 100.0, 1) + "%" } ?: "비교 표본 없음"),
            row("Known-match 분모", "일치 / (일치 + 불일치) · UNKNOWN 제외"),
            row("UNKNOWN rate", value.unknownStableRate?.let { decimal(it * 100.0, 1) + "%" } ?: "비교 표본 없음"),
            row("UNKNOWN 분모", "UNKNOWN / (일치 + 불일치 + UNKNOWN) · 세션 운동 확정 후 표본만 포함")
        ))
    )

    private fun detections(title: String, counts: Map<String, Long>, processed: Long): MetricGroupUiModel =
        MetricGroupUiModel(title, if (processed == 0L) listOf(row("검출 표본", "실제 prediction 표본 없음"))
        else listOf("SQUAT", "SHOULDER_PRESS", "UNKNOWN").map { key -> row(exercise(key), (counts[key] ?: 0L).toString()) })

    private fun exercise(value: String): String = when (value) { "SQUAT" -> "스쿼트"; "SHOULDER_PRESS" -> "숄더 프레스"; else -> "UNKNOWN" }
    private fun row(label: String, value: String) = MetricRowUiModel(label, value)
    private fun decimal(value: Double, places: Int = 3) = String.format(Locale.KOREA, "%.${places}f", value)
    private fun timestamp(value: Long): String = if (value > 0L)
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date(value)) else "정보 없음"
}

/** Per-screen ephemeral state. Opening a saved report never opens its developer details by default. */
class YoloDiagnosticsExpansionState {
    var expanded: Boolean = false
        private set

    fun toggle(): Boolean {
        expanded = !expanded
        return expanded
    }
}
