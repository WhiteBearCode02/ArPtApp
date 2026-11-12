package com.example.arptapp.domain.detection

import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.presentation.report.YoloSessionDiagnosticsPresentation
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Synthetic buffers prove wiring/bounds only. These tests CANNOT promote the real export. */
class Exp04ContractTest {
    private val profile = YoloModelProfiles.EXP04
    private fun metadata(p: YoloModelProfile) = YoloTensorMetadata(listOf(TensorSpec(p.inputShape, "FLOAT32")), listOf(TensorSpec(p.outputShape, "FLOAT32")))
    private val transform = Exp02FrameTransform.create(640, 640)
    private fun output() = ByteBuffer.allocateDirect(profile.outputBytes).order(ByteOrder.nativeOrder())
    private fun anchor(b: ByteBuffer, index: Int, classId: Int, score: Float) {
        fun set(channel: Int, value: Float) { b.putFloat((channel * 8400 + index) * 4, value) }
        set(0, .5f); set(1, .5f); set(2, .25f); set(3, .25f); set(4 + classId, score)
    }
    @Test fun exactIdentityAndSevenClassOrderAreIndependentFromCanonicalExp02() {
        assertEquals(9898949L, profile.descriptor.bytes)
        assertEquals("3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d", profile.descriptor.sha256)
        assertEquals(listOf("SQUAT", "SHOULDER_PRESS", "BICEP_CURL", "PUSH_UP", "BENT_OVER_ROW", "TRICEPS_EXTENSION", "SIDE_BEND"), profile.classMapping.values.map { it.name })
        assertEquals(listOf(1, 11, 8400), profile.outputShape); assertEquals(369600, profile.outputBytes)
        assertEquals(KnownYoloModels.EXP02, YoloModelPolicy.identity)
        assertEquals(2, YoloModelProfiles.EXP03.classMapping.size)
        assertFalse(profile.supportedExercises.any { it in setOf(ExerciseType.LUNGE, ExerciseType.IDLE, ExerciseType.UNKNOWN) })
    }
    @Test fun immutableProfileAndWrongIdentityReject() {
        assertTrue(runCatching { (profile.classMapping as MutableMap<Int, ExerciseType>)[7] = ExerciseType.LUNGE }.isFailure)
        assertFalse(YoloModelIdentity.matches(KnownYoloModels.EXP03.fileName, KnownYoloModels.EXP03.bytes, KnownYoloModels.EXP03.sha256, profile.descriptor))
        assertFalse(YoloModelIdentity.matches(profile.descriptor.fileName, profile.descriptor.bytes, profile.descriptor.sha256, KnownYoloModels.EXP03))
        assertTrue(runCatching { YoloModelProfiles.forIdentity(profile.descriptor.copy(bytes = 1)) }.isFailure)
    }
    @Test fun tensorShapeDtypeAndCrossModelMetadataAreCheckedPerProfile() {
        assertTrue(metadata(profile).matchesExpected(profile)); assertFalse(metadata(profile).matchesExpected())
        for (old in listOf(YoloModelProfiles.EXP02, YoloModelProfiles.EXP03)) {
            assertTrue(metadata(old).matchesExpected(old)); assertFalse(metadata(old).matchesExpected(profile))
            assertFalse(metadata(profile).matchesExpected(old))
        }
        assertFalse(metadata(profile).copy(outputs = listOf(TensorSpec(profile.outputShape, "FLOAT16"))).matchesExpected(profile))
    }
    @Test fun genericDecoderAndClassAwareNmsRetainAllSevenOverlappingClasses() {
        val b = output(); repeat(7) { anchor(b, it, it, .9f - it * .01f) }
        anchor(b, 7, 2, .7f) // lower confidence duplicate of class 2 is suppressed, not other classes
        val result = YoloRaw640Algorithms.postProcessor(profile).process(YoloRaw640Algorithms.decoder(profile).decode(b, transform))
        assertEquals(profile.classMapping.values.toList(), result.map { it.exerciseType })
        assertTrue(result.all { it.boundingBox == DetectionBox(240f, 240f, 400f, 400f) })
    }
    @Test fun decoderRejectsWrongSizeNonfiniteAndOutOfRangeClassScores() {
        val decoder = YoloRaw640Algorithms.decoder(profile)
        assertTrue(runCatching { decoder.decode(ByteBuffer.allocateDirect(4 * 6 * 8400).order(ByteOrder.nativeOrder()), transform) }.isFailure)
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 1.01f)) {
            val b = output(); b.putFloat((10 * 8400) * 4, invalid)
            assertTrue(runCatching { decoder.decode(b, transform) }.isFailure)
        }
    }
    @Test fun twoClassGenericRegressionUsesIdenticalTieAndThresholdRules() {
        val b = ByteBuffer.allocateDirect(YoloModelProfiles.EXP02.outputBytes).order(ByteOrder.nativeOrder())
        anchor(b, 0, 0, .8f); b.putFloat((5 * 8400) * 4, .8f)
        val historical = Exp02OutputDecoder().decode(b, transform)
        for (old in listOf(YoloModelProfiles.EXP02, YoloModelProfiles.EXP03)) {
            assertEquals(historical, YoloRaw640Algorithms.decoder(old).decode(b, transform))
            assertEquals(0, historical.candidates.single().classId)
            assertEquals(Exp02PostProcessor().process(historical), YoloRaw640Algorithms.postProcessor(old).process(historical))
        }
    }
    @Test fun stabilizerSupportsSevenButNeverLungeAndLegacyStaysTwoClass() {
        val policy = YoloStabilizationPolicy(.6f)
        for (type in profile.supportedExercises) {
            val stabilizer = YoloExerciseStabilizer(policy, profile)
            var stable = ExerciseType.UNKNOWN
            repeat(4) { stable = stabilizer.observe(ExerciseDetection(type, .8f, DetectionSource.YOLO), it * 250L) }
            assertEquals(type, stable)
        }
        for (p in listOf(profile, YoloModelProfiles.EXP02)) {
            val s = YoloExerciseStabilizer(policy, p)
            repeat(5) { assertEquals(ExerciseType.UNKNOWN, s.observe(ExerciseDetection(ExerciseType.LUNGE, .99f, DetectionSource.YOLO), it * 250L)) }
        }
    }
    @Test fun realContractNeverAdmitsCameraInferenceEvenWithExplicitDebugGate() {
        val c = Exp04YoloContract.components<Int>(YoloPreprocessor { error("No camera copy/preprocessing permitted") })
        assertEquals(ExportContractVerification.NOT_VERIFIED, c.verification)
        var calls = 0
        val r = object : YoloTensorRuntime {
            override val modelId = profile.descriptor.modelId
            override fun metadata() = metadata(profile)
            override fun run(input: ByteBuffer, output: ByteBuffer) { calls++ }
            override fun close() = Unit
        }
        YoloExerciseDetector({ r }, c, runtimePolicy = YoloRuntimePolicy.forBuild(true, true)).use {
            assertEquals(DetectorStatus.CONTRACT_NOT_VERIFIED, it.detect(0).status); assertFalse(it.detect(0).predictionAllowed)
        }
        assertEquals(0, calls)
    }
    @Test fun syntheticVerifiedPortAllocatesElevenChannelsNotGlobalSix() {
        // Test-double contract only, NEVER Exp04YoloContract or an actual-model promotion.
        val prepared = YoloPreparedInput(ByteBuffer.allocateDirect(profile.inputBytes).order(ByteOrder.nativeOrder()), transform)
        val c = YoloContractComponents(ExportContractVerification.VERIFIED, YoloPreprocessor<Int> { prepared },
            YoloRaw640Algorithms.decoder(profile), YoloRaw640Algorithms.postProcessor(profile), YoloStabilizationPolicy(.6f), profile.descriptor.modelId)
        var allocated = 0
        val runtime = object : YoloTensorRuntime {
            override val modelId = profile.descriptor.modelId
            override fun metadata() = metadata(profile)
            override fun run(input: ByteBuffer, output: ByteBuffer) { allocated = output.capacity(); anchor(output, 0, 6, .9f) }
            override fun close() = Unit
        }
        YoloExerciseDetector({ runtime }, c, runtimePolicy = YoloRuntimePolicy.forBuild(true, true)).use {
            assertEquals(ExerciseType.SIDE_BEND, it.detect(0).exerciseType)
        }
        assertEquals(profile.outputBytes, allocated); assertEquals(ExportContractVerification.NOT_VERIFIED, Exp04YoloContract.verification)
    }
    @Test fun explicitSandboxOnlyAndReleaseOff() {
        assertEquals(profile.descriptor, YoloBuildSelection.identity("EXP04", true, true, "com.example.arptapp.exp04"))
        assertTrue(runCatching { YoloBuildSelection.identity("EXP04", false, true, "com.example.arptapp.exp04") }.isFailure)
        assertTrue(runCatching { YoloBuildSelection.identity("EXP04", true, false, "com.example.arptapp") }.isFailure)
        assertTrue(runCatching { YoloBuildSelection.identity("EXP04", true, true, "com.example.arptapp.exp03") }.isFailure)
        assertFalse(YoloRuntimePolicy.forBuild(false, true).predictionAllowed(ExportContractVerification.VERIFIED, DetectorStatus.READY))
    }
    @Test fun localDiagnosticsUseModelSpecificSevenClassCountsAndKeepLegacyHistory() {
        val collector = YoloSessionDiagnosticsCollector(profile.descriptor)
        val s = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
            ExerciseDetection(ExerciseType.SIDE_BEND, .8f, DetectionSource.YOLO), timings = DetectionTimings(1, 8, 1, 10),
            contractVerification = ExportContractVerification.VERIFIED, shadowRuntimeEnabled = true), ExerciseType.SIDE_BEND)
        val token = collector.reset(100, 0, CameraView.FRONT, s)
        collector.observeFrameSeen(token); collector.observeAdmission(token); collector.observeSnapshot(token, s, ExerciseType.SIDE_BEND, 0, 500)
        val d = requireNotNull(collector.finish(token, 1100, 1000, ExerciseType.SIDE_BEND, 0))
        val loaded = requireNotNull(YoloSessionDiagnosticsCodec.decode(YoloSessionDiagnosticsCodec.encode(d)))
        assertEquals("SUPPORTED", loaded.comparisonApplicability); assertEquals(1L, loaded.matchingStableFrames)
        assertEquals(1L, loaded.stableDetectionCounts["SIDE_BEND"]); assertEquals(8, loaded.rawDetectionCounts.size)
        assertTrue(YoloSessionDiagnosticsPresentation.groups(loaded).toString().contains("덤벨 사이드 벤드"))
        assertNull(YoloSessionDiagnosticsCodec.encode(d.copy(modelId = KnownYoloModels.EXP03.modelId, modelSha256 = KnownYoloModels.EXP03.sha256)))
    }
}
