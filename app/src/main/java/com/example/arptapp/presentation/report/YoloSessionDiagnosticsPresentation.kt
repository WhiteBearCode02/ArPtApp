package com.example.arptapp.presentation.report

import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.domain.detection.YoloSessionDiagnostics
import com.example.arptapp.presentation.diagnostics.YoloDiagnosticGuide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local developer comparison only. This mapper cannot calculate posture scores or call a server. */
object YoloSessionDiagnosticsPresentation {
    fun visibleDiagnostics(debug: Boolean, json: String?): YoloSessionDiagnostics? =
        if (debug) YoloSessionDiagnosticsCodec.decode(json) else null

    /** Default user-facing groups. Counts remain frames, never reps; agreement is not accuracy. */
    fun groups(value: YoloSessionDiagnostics): List<MetricGroupUiModel> = listOf(
        MetricGroupUiModel("한눈에 보는 보조 AI 기록", listOf(
            row("이 섹션의 역할", YoloDiagnosticGuide.ROLE),
            row("분석 완료 화면", if (value.processedFrames > 0) "${value.processedFrames}장 · 운동 횟수가 아닌 화면 장수입니다"
                else "완료된 분석 기록 없음 · 0점이나 오답을 뜻하지 않습니다"),
            row("기록 종료 시 모델 상태", YoloDiagnosticGuide.status(value.modelStatus)),
            row("기록 중인 운동", YoloDiagnosticGuide.exercise(value.expectedSessionExercise)),
            row("촬영 방향", YoloDiagnosticGuide.direction(value.cameraView)),
            row("기록 시간", "${timestamp(value.startedAt)} ~ ${timestamp(value.endedAt)}")
        )),
        MetricGroupUiModel("AI가 무슨 운동으로 보았나요?", listOf(
            row("한 화면씩 본 결과", friendlyCounts(value.rawDetectionCounts, value.processedFrames) +
                "\n순간적으로 달라질 수 있는 판단입니다."),
            row("연속 화면을 모은 판단", friendlyCounts(value.stableDetectionCounts, value.processedFrames) +
                "\n판단 보류는 오답이 아니라 아직 결과가 모이지 않은 상태입니다."),
            row("평균 모델 확신도", (if (value.confidenceSampleCount > 0) YoloDiagnosticGuide.confidence(value.averageConfidence) else "미측정 · 검출 표본 없음") +
                "\n모델의 판단 강도이며 실제 정답률이나 자세 점수가 아닙니다. 검출이 없는 화면을 0%로 넣지 않습니다.")
        )),
        MetricGroupUiModel("화면 처리는 얼마나 진행됐나요?", listOf(
            row("처리 단계", "입력 후보 ${value.totalFramesSeen}장 → 처리 허용 ${value.admittedFrames}장 → 분석 완료 ${value.processedFrames}장"),
            row("수가 다른 이유", "바쁜 동안 화면을 모두 쌓아두지 않습니다. 후보 수는 앱의 분석 간격을 통과한 화면만 세며 원본 카메라 전체 장수가 아닙니다."),
            row("YOLO 평균 처리 속도", (value.averageProcessedFps?.let { "초당 ${YoloDiagnosticGuide.decimal(it, 2)}장" } ?: "미측정") +
                "\n분석 완료 장수 / 활성 세션 시간입니다. 카메라·관절 분석의 속도나 인식 정확도가 아닙니다."),
            row("한 화면 평균 처리 시간", (value.averageTotalMs?.let { "${YoloDiagnosticGuide.decimal(it, 2)} ms" } ?: "미측정") +
                "\n화면 준비·AI 추론·결과 정리를 합친 시간입니다. 1,000 ms는 1초입니다."),
            row("기록 중 가장 높은 발열 단계", YoloDiagnosticGuide.thermal(value.maxThermalStatus) +
                "\nAndroid가 알리는 기기 제한 단계입니다. 섭씨 온도나 체온이 아니며, 미측정은 제한 없음과 다릅니다.")
        )),
        MetricGroupUiModel("앱이 확정한 운동과 같은가요?", listOf(
            row("비교 결과", "같음 ${value.matchingStableFrames}장 / 다름 ${value.mismatchingStableFrames}장 / 판단 보류 ${value.unknownStableFrames}장"),
            row("판단 가능한 결과끼리의 일치 비율", (value.knownMatchRate?.let { YoloDiagnosticGuide.confidence(it) } ?: "비교 표본 없음") +
                "\n같음 / (같음 + 다름). 판단 보류는 제외합니다. 실제 정답과 비교한 모델 정확도가 아닙니다."),
            row("판단 보류 비율", (value.unknownStableRate?.let { YoloDiagnosticGuide.confidence(it) } ?: "비교 표본 없음") +
                "\n보류 / (같음 + 다름 + 보류). 앱의 운동이 확정된 뒤의 표본만 비교합니다."),
            row("비교하지 않은 화면", "${value.unknownSessionFrames}장 · 앱에서 운동을 확정하기 전이므로 비교 대상에서 제외했습니다.")
        ))
    )

    private fun friendlyCounts(counts: Map<String, Long>, processed: Long): String = if (processed == 0L)
        "분석 완료 표본 없음" else listOf("SQUAT", "SHOULDER_PRESS", "UNKNOWN").joinToString(" / ") { key ->
            "${if (key == "UNKNOWN") "판단 보류" else YoloDiagnosticGuide.exercise(key)} ${counts[key] ?: 0L}장"
        }

    /** Original lossless developer fields stay behind a separate, initially closed disclosure. */
    fun technicalGroups(value: YoloSessionDiagnostics): List<MetricGroupUiModel> = listOf(
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
