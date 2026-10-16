package com.example.arptapp.presentation.report

import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.domain.detection.YoloSessionDiagnostics
import org.junit.Assert.*
import org.junit.Test

class YoloSessionDiagnosticsPresentationTest {
    private fun missing() = YoloSessionDiagnostics(
        modelStatus = "MODEL_MISSING", contractStatus = "NOT_VERIFIED", predictionEnabled = false,
        startedAt = 1000, endedAt = 2000
    )

    private fun rows(value: YoloSessionDiagnostics) = YoloSessionDiagnosticsPresentation.groups(value)
        .flatMap { it.rows }.associate { it.label to it.value }

    @Test fun `release hides even valid local diagnostics`() {
        val raw = YoloSessionDiagnosticsCodec.encode(missing())!!
        assertNull(YoloSessionDiagnosticsPresentation.visibleDiagnostics(false, raw))
        assertNotNull(YoloSessionDiagnosticsPresentation.visibleDiagnostics(true, raw))
    }

    @Test fun `legacy null malformed and oversized diagnostics stay hidden`() {
        listOf(null, "", "invalid", "{}", "a".repeat(YoloSessionDiagnosticsCodec.MAX_JSON_CHARS + 1))
            .forEach { assertNull(YoloSessionDiagnosticsPresentation.visibleDiagnostics(true, it)) }
    }

    @Test fun `model missing and unverified state do not imply predictions exist`() {
        val groups = YoloSessionDiagnosticsPresentation.groups(missing())
        assertEquals("MODEL_MISSING", rows(missing())["모델 상태"])
        assertEquals("NOT_VERIFIED", rows(missing())["Export 계약"])
        assertEquals("0", rows(missing())["실제 처리 프레임"])
        assertTrue(groups.filter { it.title.startsWith("YOLO") }.all {
            it.rows.single().value == "실제 prediction 표본 없음"
        })
        assertTrue(rows(missing())["Prediction"]!!.startsWith("비활성"))
        assertEquals("미측정", rows(missing())["성능"])
        assertTrue(rows(missing())["Confidence"]!!.contains("0으로 대체하지 않음"))
        assertTrue(rows(missing()).keys.any { it.contains("기준 SHA256") && it.contains("로드 증명 아님") })
    }

    @Test fun `ready but unverified does not display activated prediction`() {
        val rows = rows(missing().copy(modelStatus = "READY"))
        assertEquals("READY", rows["모델 상태"])
        assertEquals("NOT_VERIFIED", rows["Export 계약"])
        assertTrue(rows["Prediction"]!!.startsWith("비활성"))
    }

    @Test fun `verified stored status can be shown without changing runtime contract`() {
        val rows = rows(missing().copy(modelStatus = "READY", contractStatus = "VERIFIED", predictionEnabled = true))
        assertEquals("VERIFIED", rows["Export 계약"])
        assertEquals("활성", rows["Prediction"])
    }

    @Test fun `known match excludes UNKNOWN and unknown rate uses separate stated denominator`() {
        val value = missing().copy(expectedSessionExercise = "SQUAT", cameraView = "SIDE",
            matchingStableFrames = 92, mismatchingStableFrames = 3, unknownStableFrames = 15)
        val rows = rows(value)
        assertEquals("스쿼트", rows["세션 운동"])
        assertEquals("측면", rows["촬영 방향"])
        assertEquals("96.8%", rows["Known-match rate"])
        assertEquals("13.6%", rows["UNKNOWN rate"])
        assertTrue(rows["Known-match 분모"]!!.contains("UNKNOWN 제외"))
        assertTrue(rows["UNKNOWN 분모"]!!.contains("세션 운동 확정 후"))
    }

    @Test fun `confidence shows only stored detection statistics and performance stays separate`() {
        val value = missing().copy(processedFrames = 2, confidenceSampleCount = 1,
            averageConfidence = 0.87, minConfidence = 0.87, maxConfidence = 0.87,
            rawDetectionCounts = mapOf("SQUAT" to 1, "UNKNOWN" to 1),
            stableDetectionCounts = mapOf("SQUAT" to 1, "UNKNOWN" to 1),
            averageProcessedFps = 3.9, averageInferenceMs = 41.0, averageTotalMs = 54.0, maxThermalStatus = 2)
        val rows = rows(value)
        assertEquals("0.870", rows["평균 confidence"])
        assertEquals("1", rows["Detection 표본 수"])
        assertEquals("3.90 FPS", rows["평균 처리 FPS"])
        assertEquals("41.00 ms", rows["평균 inference"])
        assertEquals("2", rows["최대 thermal 단계"])
        assertFalse(rows.keys.any { it.contains("점수") || it.contains("칼로리") })
    }

    @Test fun `each report begins collapsed and expansion has no persistence side effects`() {
        val first = YoloDiagnosticsExpansionState()
        assertFalse(first.expanded)
        assertTrue(first.toggle())
        assertFalse(YoloDiagnosticsExpansionState().expanded)
        assertFalse(first.toggle())
    }
}
