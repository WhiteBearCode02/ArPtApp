package com.example.arptapp.domain.detection

import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.domain.classifier.ExerciseType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.security.MessageDigest

/** Pure ports/algorithm tests are NOT actual-model/Android evidence and cannot promote EXP03. */
class Exp03ContractTest {
    private val current = KnownYoloModels.EXP03
    private fun diagnostic(model: YoloModelDescriptor = current) = YoloSessionDiagnostics(
        modelId = model.modelId, modelSha256 = model.sha256, modelStatus = "MODEL_MISSING",
        contractStatus = "NOT_VERIFIED", predictionEnabled = false, startedAt = 10, endedAt = 20)

    @Test fun canonicalIdentityUsesMeasuredExp03SizeAndUserConfirmedHash() {
        assertEquals("yolo26n_exp03_final_fp32", YoloModelPolicy.MODEL_ID)
        assertEquals(9894032L, YoloModelPolicy.CANONICAL_MODEL_BYTES)
        assertEquals(current.fileName, YoloModelPolicy.FILE_NAME)
        assertEquals(current.sha256, YoloModelPolicy.CANONICAL_SHA256)
        assertTrue(YoloModelIdentity.matches(current.fileName, current.bytes, current.sha256))
    }
    @Test fun wrongNameSizeAndHashAreIndependentlyRejected() {
        assertFalse(YoloModelIdentity.matches(KnownYoloModels.EXP02.fileName, current.bytes, current.sha256))
        assertFalse(YoloModelIdentity.matches(current.fileName, current.bytes - 1, current.sha256))
        assertFalse(YoloModelIdentity.matches(current.fileName, current.bytes, KnownYoloModels.EXP02.sha256))
        assertFalse(YoloModelIdentity.matches(current.fileName, current.bytes, current.sha256.uppercase()))
    }
    @Test fun nativeByteValidationRejectsBadSizeAndSameSizeWrongHashWithoutMovingBuffer() {
        for (size in listOf(4, current.bytes.toInt())) {
            val buffer = ByteBuffer.allocate(size)
            assertTrue(runCatching { YoloModelIdentity.validate(buffer) }.exceptionOrNull() is YoloModelIdentityMismatch)
            assertEquals(0, buffer.position())
        }
    }
    @Test fun newDiagnosticsRoundTripWithExp03Identity() {
        val decoded = requireNotNull(YoloSessionDiagnosticsCodec.decode(YoloSessionDiagnosticsCodec.encode(diagnostic())))
        assertEquals(current.modelId, decoded.modelId); assertEquals(current.sha256, decoded.modelSha256)
    }
    @Test fun historicalExp02JsonAndCanonicalHashRemainIdentical() {
        val legacy = diagnostic(KnownYoloModels.EXP02).copy(rawDetectionCounts = mapOf("SQUAT" to 0L, "SHOULDER_PRESS" to 0L, "UNKNOWN" to 0L),
            stableDetectionCounts = mapOf("SQUAT" to 0L, "SHOULDER_PRESS" to 0L, "UNKNOWN" to 0L))
        val original = Json { encodeDefaults = true; explicitNulls = true }.encodeToString(legacy)
        val decoded = requireNotNull(YoloSessionDiagnosticsCodec.decode(original))
        val roundTrip = requireNotNull(YoloSessionDiagnosticsCodec.encode(decoded))
        assertEquals(original, roundTrip)
        fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toList()
        assertEquals(hash(original), hash(roundTrip))
        assertEquals(KnownYoloModels.EXP02.modelId, decoded.modelId)
        assertNull(decoded.shadowRuntimeEnabled)
    }
    @Test fun crossModelAndUnknownIdentityPairsNeverDecode() {
        val raw = requireNotNull(YoloSessionDiagnosticsCodec.encode(diagnostic()))
        assertNull(YoloSessionDiagnosticsCodec.decode(raw.replace(current.sha256, KnownYoloModels.EXP02.sha256)))
        assertNull(YoloSessionDiagnosticsCodec.decode(raw.replace(current.modelId, KnownYoloModels.EXP02.modelId)))
        assertNull(YoloSessionDiagnosticsCodec.decode(raw.replace(current.modelId, "unknown")))
        assertNull(YoloSessionDiagnosticsCodec.decode(raw.replace(current.sha256, "0".repeat(64))))
    }
    @Test fun missingIdentityCannotBeFilledFromNewDefaults() {
        val raw = requireNotNull(YoloSessionDiagnosticsCodec.encode(diagnostic()))
        assertNull(YoloSessionDiagnosticsCodec.decode(raw.replace("\"modelId\":\"${current.modelId}\",", "")))
        assertNull(YoloSessionDiagnosticsCodec.decode(raw.replace("\"modelSha256\":\"${current.sha256}\",", "")))
    }
    @Test fun exp03CannotInheritExp02Verification() {
        assertEquals(ExportContractVerification.VERIFIED, Exp02YoloContract.verification)
        assertEquals(ExportContractVerification.NOT_VERIFIED, Exp03YoloContract.verification)
        assertEquals(current.modelId, Exp03YoloContract.components<Int>(YoloPreprocessor { error("blocked") }).modelId)
    }
    @Test fun verifiedLegacyContractCannotRunAgainstCurrentNativeIdentity() {
        var calls = 0
        val native = object : YoloTensorRuntime {
            override fun metadata() = YoloTensorMetadata(listOf(TensorSpec(YoloModelPolicy.inputShape, "FLOAT32")),
                listOf(TensorSpec(YoloModelPolicy.outputShape, "FLOAT32")))
            override fun run(input: ByteBuffer, output: ByteBuffer) { calls++ }
            override fun close() = Unit
        }
        val detector = YoloExerciseDetector({ native }, Exp02YoloContract.components<Int>(YoloPreprocessor { error("must not run") }),
            runtimePolicy = YoloRuntimePolicy.forBuild(true, true))
        val result = detector.detect(0)
        assertEquals(DetectorStatus.CONTRACT_NOT_VERIFIED, result.status)
        assertEquals(ExportContractVerification.NOT_VERIFIED, result.contractVerification)
        assertFalse(result.predictionAllowed); assertEquals(0, calls)
    }
    @Test fun classMappingIsUnchangedAndSharedAlgorithmsKeepStrictThresholds() {
        assertEquals(mapOf(0 to ExerciseType.SQUAT, 1 to ExerciseType.SHOULDER_PRESS), YoloModelPolicy.classMapping)
        assertEquals(.10f, Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_RAW_CONFIDENCE, 0f)
        assertEquals(.60f, Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_STABLE_CONFIDENCE, 0f)
        assertEquals(.70f, Exp02DetectionPolicy.IOU_THRESHOLD, 0f)
        assertEquals(300, Exp02DetectionPolicy.MAX_DETECTIONS)
        assertEquals(250L, YoloShadowPolicy.MIN_FRAME_INTERVAL_MS)
    }
}
