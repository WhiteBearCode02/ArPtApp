package com.example.arptapp.domain.detection

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import java.util.Locale

/** Scalar-only live diagnostics. Session persistence uses a separate aggregate DTO, never this frame result. */
data class DetectorComparison(
    val yolo: YoloShadowSnapshot, val fallbackDetected: ExerciseType, val sessionExerciseType: ExerciseType,
    val cameraView: CameraView, val posePeopleCount: Int, val thermalStatus: Int,
    val processedFps: Double? = null
)
object DetectorComparisonFormatter {
    fun format(data: DetectorComparison): String = buildString {
        val result = data.yolo.result
        append("YOLO SHADOW ${YoloModelPolicy.MODEL_ID} · ${result.status}\n")
        append("exportContract=${result.contractVerification} · ")
        append(if (result.contractVerification == ExportContractVerification.VERIFIED && result.status == DetectorStatus.READY)
            "prediction 준비됨\n" else "prediction 비활성\n")
        append("raw=${result.exerciseType} ")
        append(result.selected?.confidence?.takeIf { it.isFinite() && it in 0f..1f }?.let {
            String.format(Locale.ROOT, "%.3f", it)
        } ?: "미측정")
        append(" · stable=${data.yolo.stableExercise}\n")
        append("fallback=${data.fallbackDetected} · session=${data.sessionExerciseType} · view=${data.cameraView}\n")
        val t = result.timings
        append(if (t == null) "pre/infer/post/total=미측정" else
            "pre=${t.preprocessMs}ms infer=${t.inferenceMs}ms post=${t.postprocessMs}ms total=${t.totalMs}ms")
        append(" · thermal=${data.thermalStatus.coerceIn(-1, 6)}")
        append("\nYOLO processedFPS=")
        append(data.processedFps?.takeIf { it.isFinite() && it >= 0 }?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "미측정")
        if (data.posePeopleCount > 1 || result.detectionCount > 1) append(" · MULTI_PERSON_UNASSOCIATED")
    }
}
