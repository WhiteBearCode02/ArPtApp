package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** All VERIFIED contracts here are test doubles, NOT evidence for the actual EXP02 export. */
class YoloContractTest {
    private val metadata = YoloTensorMetadata(listOf(TensorSpec(listOf(1, 3, 640, 640), "FLOAT32")),
        listOf(TensorSpec(listOf(1, 6, 8400), "FLOAT32")))
    private class Runtime(private val spec: YoloTensorMetadata, private val shouldThrow: Boolean = false) : YoloTensorRuntime {
        var calls = 0; var closes = 0
        override fun metadata() = spec
        override fun run(input: ByteBuffer, output: ByteBuffer) { calls++; if (shouldThrow) error("synthetic failure") }
        override fun close() { closes++ }
    }
    private val transform = object : YoloFrameTransform {}
    private fun fakeContract(detections: List<ExerciseDetection> = listOf(ExerciseDetection(ExerciseType.SQUAT, .9f, DetectionSource.YOLO))) =
        YoloContractComponents<Int>(ExportContractVerification.VERIFIED,
            YoloPreprocessor { YoloPreparedInput(ByteBuffer.allocateDirect(4 * 3 * 640 * 640).order(ByteOrder.nativeOrder()), transform) },
            YoloOutputDecoder { _, _ -> detections }, YoloPostProcessor { it }, YoloStabilizationPolicy(.7f))
    @Test fun expectedTensorMetadataMatches() { assertTrue(metadata.matchesExpected()) }
    @Test fun nchwAndNhwcAreNotInterchangeable() {
        assertFalse(metadata.copy(inputs = listOf(TensorSpec(listOf(1, 640, 640, 3), "FLOAT32"))).matchesExpected())
    }
    @Test fun transposedOutputIsRejected() {
        assertFalse(metadata.copy(outputs = listOf(TensorSpec(listOf(1, 8400, 6), "FLOAT32"))).matchesExpected())
    }
    @Test fun wrongDtypeIsRejected() {
        assertFalse(metadata.copy(inputs = listOf(TensorSpec(YoloModelPolicy.inputShape, "INT8"))).matchesExpected())
        assertFalse(metadata.copy(outputs = listOf(TensorSpec(YoloModelPolicy.outputShape, "FLOAT16"))).matchesExpected())
    }
    @Test fun extraInputOrOutputIsRejected() {
        assertFalse(metadata.copy(inputs = metadata.inputs + metadata.inputs).matchesExpected())
        assertFalse(metadata.copy(outputs = emptyList()).matchesExpected())
    }
    @Test fun missingModelIsUnavailableWithoutCrash() {
        val detector = YoloExerciseDetector<Int>({ throw MissingYoloModel() })
        assertEquals(DetectorStatus.MODEL_MISSING, detector.initialize().status)
        assertEquals(ExerciseType.UNKNOWN, detector.detect(0).exerciseType)
        detector.close()
    }
    @Test fun failedNativeInitializationIsIsolated() {
        assertEquals(DetectorStatus.INITIALIZATION_FAILED,
            YoloExerciseDetector<Int>({ throw UnsatisfiedLinkError("synthetic JNI error") }).initialize().status)
    }
    @Test fun shapeMismatchClosesRuntimeAndNeverInvokesInference() {
        val runtime = Runtime(metadata.copy(outputs = emptyList()))
        val detector = YoloExerciseDetector<Int>({ runtime })
        assertEquals(DetectorStatus.TENSOR_MISMATCH, detector.detect(0).status)
        assertEquals(0, runtime.calls); assertEquals(1, runtime.closes)
    }
    @Test fun validModelWithUnverifiedContractNeverPreprocessesOrRuns() {
        val runtime = Runtime(metadata)
        val contract = UnverifiedYoloContract.components<Int>().copy(preprocessor = YoloPreprocessor { error("must not run") })
        val detector = YoloExerciseDetector({ runtime }, contract)
        repeat(10) { assertEquals(DetectorStatus.CONTRACT_NOT_VERIFIED, detector.detect(it).status) }
        assertEquals(0, runtime.calls); assertEquals(1, runtime.closes)
    }
    @Test fun confidencePolicyMustAlsoBeVerifiedBeforeAnyRun() {
        val runtime = Runtime(metadata)
        val detector = YoloExerciseDetector({ runtime }, fakeContract().copy(stabilization = YoloStabilizationPolicy(null)))
        assertEquals(DetectorStatus.CONTRACT_NOT_VERIFIED, detector.detect(0).status)
        assertEquals(0, runtime.calls)
    }
    @Test fun malformedPreparedTensorCannotReachNativeInvocation() {
        val runtime = Runtime(metadata)
        val contract = fakeContract().copy(preprocessor = YoloPreprocessor {
            YoloPreparedInput(ByteBuffer.allocateDirect(4), transform)
        })
        val detector = YoloExerciseDetector({ runtime }, contract)
        assertEquals(DetectorStatus.INFERENCE_FAILED, detector.detect(0).status)
        assertEquals(0, runtime.calls)
    }
    @Test fun eachUnverifiedConversionPortFailsClosed() {
        val ports = UnverifiedYoloContract.components<Int>()
        listOf<() -> Unit>({ ports.preprocessor.preprocess(0) },
            { ports.decoder.decode(ByteBuffer.allocate(1), transform) }, { ports.postProcessor.process(emptyList()) }).forEach { action ->
            try { action(); fail("Unverified conversion executed") } catch (_: ExportContractNotVerified) { /* expected */ }
        }
    }
    @Test fun syntheticVerifiedPortsCanBeInjectedLaterWithoutChangingDetector() {
        val runtime = Runtime(metadata)
        var tick = 0L
        val detector = YoloExerciseDetector({ runtime }, fakeContract(), clockMs = { tick++ })
        val result = detector.detect(0)
        assertEquals(DetectorStatus.READY, result.status)
        assertEquals(ExerciseType.SQUAT, result.exerciseType)
        assertEquals(1, runtime.calls); assertNotNull(result.timings)
        detector.close(); detector.close()
        assertEquals(1, runtime.closes)
        assertEquals(DetectorStatus.CLOSED, detector.detect(0).status)
    }
    @Test fun inferenceFailurePermanentlyDisablesOnlyYolo() {
        val runtime = Runtime(metadata, true)
        val detector = YoloExerciseDetector({ runtime }, fakeContract())
        repeat(2) { assertEquals(DetectorStatus.INFERENCE_FAILED, detector.detect(0).status) }
        assertEquals(1, runtime.calls); assertEquals(1, runtime.closes)
    }
    @Test fun invalidCanonicalConfidenceIsNotForcedIntoSupportedClass() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f).forEach { value ->
            val runtime = Runtime(metadata)
            val detector = YoloExerciseDetector({ runtime }, fakeContract(listOf(ExerciseDetection(ExerciseType.SQUAT, value, DetectionSource.YOLO))))
            assertEquals(DetectorStatus.INVALID_OUTPUT, detector.detect(0).status)
        }
    }
    @Test fun unsupportedCanonicalLabelIsRejected() {
        val detector = YoloExerciseDetector({ Runtime(metadata) }, fakeContract(listOf(ExerciseDetection(ExerciseType.LUNGE, .9f, DetectionSource.YOLO))))
        assertEquals(DetectorStatus.INVALID_OUTPUT, detector.detect(0).status)
    }
    @Test fun nonFiniteCanonicalBoxCannotReachDiagnostics() {
        val contract = fakeContract(listOf(ExerciseDetection(ExerciseType.SQUAT, .9f, DetectionSource.YOLO,
            DetectionBox(Float.NaN, 0f, 10f, 10f))))
        val detector = YoloExerciseDetector({ Runtime(metadata) }, contract)
        assertEquals(DetectorStatus.INVALID_OUTPUT, detector.detect(0).status)
    }
    @Test fun classMappingHasExactlyTheTwoConfirmedLabels() {
        assertEquals(mapOf(0 to ExerciseType.SQUAT, 1 to ExerciseType.SHOULDER_PRESS), YoloModelPolicy.classMapping)
    }
}
