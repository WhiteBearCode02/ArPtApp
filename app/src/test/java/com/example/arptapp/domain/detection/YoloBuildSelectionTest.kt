package com.example.arptapp.domain.detection

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import org.junit.Assert.*
import org.junit.Test

class YoloBuildSelectionTest {
    @Test fun canonicalDebugAndReleaseSelectExp02() {
        for (debug in listOf(true, false)) assertEquals(KnownYoloModels.EXP02,
            YoloBuildSelection.identity("EXP02", debug, false, "com.example.arptapp"))
        assertEquals(KnownYoloModels.EXP02, YoloModelPolicy.identity)
    }
    @Test fun comparisonPairsAreExact() {
        assertEquals(KnownYoloModels.EXP02, YoloBuildSelection.identity("EXP02", true, true, "com.example.arptapp.exp02"))
        assertEquals(KnownYoloModels.EXP03, YoloBuildSelection.identity("EXP03", true, true, "com.example.arptapp.exp03"))
    }
    @Test fun comparisonReleaseIsRejected() {
        for (key in listOf("EXP02", "EXP03")) assertTrue(runCatching {
            YoloBuildSelection.identity(key, false, true, "com.example.arptapp.${key.lowercase()}")
        }.isFailure)
    }
    @Test fun crossedSandboxUnknownVariantAndCanonicalOverridesAreRejected() {
        for ((key, comparison, applicationId) in listOf(
            Triple("EXP02", true, "com.example.arptapp.exp03"),
            Triple("EXP03", true, "com.example.arptapp.exp02"),
            Triple("EXP03", false, "com.example.arptapp"),
            Triple("EXP03", true, "com.example.arptapp"),
            Triple("unknown", true, "com.example.arptapp.exp03"),
            Triple("EXP03", false, "com.example.arptapp.exp03"))) {
            assertTrue(runCatching { YoloBuildSelection.identity(key, true, comparison, applicationId) }.isFailure)
        }
    }
    @Test fun bothContractsKeepIdenticalStabilizationAndScheduling() {
        val noop = YoloPreprocessor<Int> { error("No frame needed") }
        val v1 = Exp02YoloContract.components(noop)
        val v2 = Exp03YoloContract.components(noop)
        assertEquals(v1.stabilization, v2.stabilization)
        assertEquals(.60f, v1.stabilization.minimumConfidence)
        assertEquals(5, v1.stabilization.windowSize)
        assertEquals(4, v1.stabilization.minimumAgreeing)
        assertEquals(250L, YoloShadowPolicy.MIN_FRAME_INTERVAL_MS)
        assertNotEquals(v1.modelId, v2.modelId)
    }
    @Test fun collectorAndCodecPreserveEachExplicitIdentity() {
        for (model in listOf(KnownYoloModels.EXP02, KnownYoloModels.EXP03)) {
            val collector = YoloSessionDiagnosticsCollector(model)
            val snapshot = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
                contractVerification = ExportContractVerification.VERIFIED, shadowRuntimeEnabled = true))
            val token = collector.reset(10, 0, CameraView.FRONT, snapshot)
            val result = requireNotNull(collector.finish(token, 20, 10, ExerciseType.SQUAT, 0))
            val restored = requireNotNull(YoloSessionDiagnosticsCodec.decode(YoloSessionDiagnosticsCodec.encode(result)))
            assertEquals(model.modelId, restored.modelId)
            assertEquals(model.sha256, restored.modelSha256)
            assertEquals(0L, restored.processedFrames)
        }
    }
    @Test fun liveFormatterUsesExplicitModelInsteadOfCanonicalLabel() {
        val snapshot = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.MODEL_MISSING, DetectionSource.YOLO))
        val data = DetectorComparison(snapshot, ExerciseType.SQUAT, ExerciseType.SQUAT, CameraView.FRONT, 1, 0,
            modelId = KnownYoloModels.EXP02.modelId)
        assertTrue(DetectorComparisonFormatter.format(data).contains("YOLO SHADOW ${KnownYoloModels.EXP02.modelId}"))
        assertFalse(DetectorComparisonFormatter.format(data).contains(KnownYoloModels.EXP03.modelId))
    }
}
