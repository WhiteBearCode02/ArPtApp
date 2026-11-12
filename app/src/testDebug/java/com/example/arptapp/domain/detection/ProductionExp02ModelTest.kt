package com.example.arptapp.domain.detection

import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.ui.camera.yolo.Exp02BitmapPreprocessor
import com.example.arptapp.ui.camera.yolo.YoloBuildProfile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** Normal-debug regression only; do not apply default-package assertions to A/B unit test variants.
 * Synthetic output never counts as actual-model accuracy.
 */
class ProductionExp02ModelTest {
    @Test fun canonicalDescriptorAndCompiledDefaultAreExactlyExp02() {
        assertEquals("EXP02", BuildConfig.YOLO_MODEL_VARIANT)
        assertFalse(BuildConfig.YOLO_AB_COMPARISON)
        assertEquals("EXP02", YoloModelPolicy.MODEL_VERSION)
        assertEquals(KnownYoloModels.EXP02, YoloModelPolicy.identity)
        assertEquals(KnownYoloModels.EXP02, YoloBuildProfile.identity)
        assertEquals(KnownYoloModels.EXP02.modelId, YoloModelPolicy.MODEL_ID)
        assertEquals(KnownYoloModels.EXP02.fileName, YoloModelPolicy.FILE_NAME)
        assertEquals(KnownYoloModels.EXP02.sha256, YoloModelPolicy.CANONICAL_SHA256)
        assertEquals(KnownYoloModels.EXP02.bytes, YoloModelPolicy.CANONICAL_MODEL_BYTES)
    }
    @Test fun productionProfileKeepsReviewedExp02PreprocessorAndContract() {
        val contract = YoloBuildProfile.components()
        assertEquals(Exp02BitmapPreprocessor::class.java, contract.preprocessor.javaClass)
        assertEquals(KnownYoloModels.EXP02.modelId, contract.modelId)
        assertEquals(ExportContractVerification.VERIFIED, contract.verification)
        assertEquals(.60f, requireNotNull(contract.stabilization.minimumConfidence), 0f)
    }
    @Test fun exp03CannotBeSelectedInNormalPackageForDebugOrRelease() {
        for (debug in listOf(true, false)) assertTrue(runCatching {
            YoloBuildSelection.identity("EXP03", debug, false, "com.example.arptapp")
        }.isFailure)
    }
    @Test fun bothActualOutputClassChannelsDecodeWithoutSwapping() {
        val transform = Exp02FrameTransform.create(640, 640)
        for ((classId, expected) in listOf(0 to ExerciseType.SQUAT, 1 to ExerciseType.SHOULDER_PRESS)) {
            val output = ByteBuffer.allocateDirect(4 * 6 * 8400).order(ByteOrder.nativeOrder())
            fun channel(index: Int, value: Float) { output.putFloat(index * 8400 * 4, value) }
            channel(0, .5f); channel(1, .5f); channel(2, .5f); channel(3, .5f)
            channel(4, if (classId == 0) .9f else .1f)
            channel(5, if (classId == 1) .9f else .1f)
            val detections = YoloRaw640Algorithms.postProcessor().process(YoloRaw640Algorithms.decoder().decode(output, transform))
            assertEquals(1, detections.size)
            assertEquals(expected, detections.single().exerciseType)
            assertEquals(.9f, requireNotNull(detections.single().confidence), 0f)
        }
    }
}
