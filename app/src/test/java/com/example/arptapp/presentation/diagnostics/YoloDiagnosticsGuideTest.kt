package com.example.arptapp.presentation.diagnostics

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.detection.*
import com.example.arptapp.presentation.report.YoloSessionDiagnosticsPresentation
import org.junit.Assert.*
import org.junit.Test

class YoloDiagnosticsGuideTest {
    private fun stored() = YoloSessionDiagnostics(modelStatus = "MODEL_MISSING", contractStatus = "NOT_VERIFIED",
        predictionEnabled = false, startedAt = 1, endedAt = 2)
    private fun comparison(result: ExerciseDetectionResult = ExerciseDetectionResult(DetectorStatus.MODEL_MISSING, DetectionSource.YOLO)) =
        DetectorComparison(YoloShadowSnapshot(result), ExerciseType.SQUAT, ExerciseType.SHOULDER_PRESS,
            CameraView.SIDE, 1, -1)
    private fun rows(value: YoloSessionDiagnostics) = YoloSessionDiagnosticsPresentation.groups(value).flatMap { it.rows }
    private fun ready() = ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
        selected = ExerciseDetection(ExerciseType.SQUAT, .87f, DetectionSource.YOLO),
        timings = DetectionTimings(10, 40, 5, 55), contractVerification = ExportContractVerification.VERIFIED,
        shadowRuntimeEnabled = true)

    @Test fun allModelStatesHaveExplanationsAndUnknownStateDoesNotImplyReady() {
        DetectorStatus.entries.forEach { assertFalse(YoloDiagnosticGuide.status(it.name).contains(it.name)) }
        assertTrue(YoloDiagnosticGuide.status("NEW_STATE").contains("확인할 수 없어요"))
    }
    @Test fun missingMeasurementsAreNotZeroOrNormal() {
        for (value in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 1.1))
            assertEquals("미측정", YoloDiagnosticGuide.confidence(value))
        assertEquals("0.0%", YoloDiagnosticGuide.confidence(0.0))
        assertEquals("확인할 수 없음", YoloDiagnosticGuide.thermal(null))
        assertEquals("확인할 수 없음", YoloDiagnosticGuide.thermal(-1))
        assertEquals("확인할 수 없음", YoloDiagnosticGuide.thermal(7))
        assertEquals("발열 제한 없음", YoloDiagnosticGuide.thermal(0))
    }
    @Test fun thermalAndDirectionNamesRemainHumanReadable() {
        (0..6).forEach { assertFalse(YoloDiagnosticGuide.thermal(it).contains("확인할 수 없음")) }
        assertEquals("선택 전", YoloDiagnosticGuide.direction("UNKNOWN"))
        assertEquals("측면", YoloDiagnosticGuide.direction("SIDE"))
    }
    @Test fun missingModelKeepsLandmarkAndSessionDifferentAndDoesNotFakeConfidence() {
        val model = YoloLiveDiagnosticsPresentation.present(comparison())
        assertTrue(model.status.contains("모델이 없어요"))
        assertTrue(model.recognition.contains("모델 확신도: 미측정"))
        assertTrue(model.recognition.contains("관절 기반 감지: 스쿼트"))
        assertTrue(model.recognition.contains("기록 중인 운동: 숄더 프레스"))
        assertTrue(model.performance.contains("세션 측정 전"))
    }
    @Test fun readyWithoutSuccessfulInferenceStaysWaiting() {
        val model = YoloLiveDiagnosticsPresentation.present(comparison(ready().copy(timings = null)))
        assertTrue(model.status.contains("첫 분석 결과"))
        assertTrue(model.recognition.contains("분석 결과 대기"))
    }
    @Test fun unverifiedOrDisabledNeverShowsSelectedPredictionAsActive() {
        for (result in listOf(ready().copy(contractVerification = ExportContractVerification.NOT_VERIFIED),
            ready().copy(shadowRuntimeEnabled = false))) {
            val model = YoloLiveDiagnosticsPresentation.present(comparison(result))
            assertTrue(model.status.contains("비활성"))
            assertTrue(model.recognition.contains("분석 결과 대기"))
            assertTrue(model.recognition.contains("확신도: 미측정"))
        }
    }
    @Test fun livePredictionKeepsSingleAndStableResultsSeparate() {
        val model = YoloLiveDiagnosticsPresentation.present(comparison(ready()).copy(processedFps = 2.5))
        assertTrue(model.recognition.contains("이번 화면: 스쿼트"))
        assertTrue(model.recognition.contains("연속 화면의 판단: 아직 판단하지 못함"))
        assertTrue(model.recognition.contains("87.0%"))
        assertTrue(model.performance.contains("초당 2.50장"))
        assertTrue(model.performance.contains("55 ms"))
    }
    @Test fun multiDetectionDoesNotClaimPersonIdentityAssociation() {
        val model = YoloLiveDiagnosticsPresentation.present(comparison(ready()).copy(posePeopleCount = 2))
        assertTrue(model.recognition.contains("같은 대상으로 단정할 수 없어요"))
    }
    @Test fun friendlyMissingReportIsNotErrorScoreAndDeveloperValuesRemainAvailable() {
        val friendly = rows(stored()).joinToString { it.value }
        assertTrue(friendly.contains("0점이나 오답을 뜻하지 않습니다"))
        assertTrue(friendly.contains("미측정 · 검출 표본 없음"))
        assertFalse(friendly.contains("MODEL_MISSING"))
        assertEquals("MODEL_MISSING", YoloSessionDiagnosticsPresentation.technicalGroups(stored())
            .flatMap { it.rows }.first { it.label == "모델 상태" }.value)
    }
    @Test fun reportAgreementAndConfidenceAreExplicitlyNotAccuracyOrPostureScore() {
        val value = stored().copy(processedFrames = 3, confidenceSampleCount = 1, averageConfidence = .87,
            matchingStableFrames = 1, mismatchingStableFrames = 1, unknownStableFrames = 1)
        val all = rows(value)
        assertTrue(all.first { it.label == "평균 모델 확신도" }.value.contains("87.0%"))
        assertTrue(all.first { it.label == "평균 모델 확신도" }.value.contains("정답률이나 자세 점수가 아닙니다"))
        assertTrue(all.first { it.label == "판단 가능한 결과끼리의 일치 비율" }.value.startsWith("50.0%"))
        assertTrue(all.first { it.label == "판단 보류 비율" }.value.startsWith("33.3%"))
        assertTrue(all.first { it.label == "분석 완료 화면" }.value.contains("운동 횟수가 아닌"))
    }
    @Test fun zeroSampleAndUnknownSessionDoNotCreatePercentages() {
        val all = rows(stored())
        assertTrue(all.first { it.label == "판단 가능한 결과끼리의 일치 비율" }.value.startsWith("비교 표본 없음"))
        assertTrue(all.first { it.label == "한 화면씩 본 결과" }.value.startsWith("분석 완료 표본 없음"))
    }
    @Test fun presentationDoesNotMutateOriginalReportOrComparison() {
        val stored = stored(); val source = comparison(ready()); val before = source.copy()
        rows(stored); YoloLiveDiagnosticsPresentation.present(source)
        assertEquals(before, source); assertEquals(0L, stored.processedFrames)
        assertFalse(stored.predictionEnabled)
    }
}
