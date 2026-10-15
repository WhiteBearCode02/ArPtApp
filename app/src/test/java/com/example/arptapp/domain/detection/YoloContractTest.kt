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
    private val transform = Exp02FrameTransform.create(640, 640)
    private fun fakeContract(detections: List<ExerciseDetection> = listOf(ExerciseDetection(ExerciseType.SQUAT, .9f, DetectionSource.YOLO))) =
        YoloContractComponents<Int>(ExportContractVerification.VERIFIED,
            YoloPreprocessor { YoloPreparedInput(ByteBuffer.allocateDirect(4 * 3 * 640 * 640).order(ByteOrder.nativeOrder()), transform) },
            YoloOutputDecoder { _, _ -> YoloDecodedOutput(emptyList(), transform) }, YoloPostProcessor { detections }, YoloStabilizationPolicy(.7f))
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
            { ports.decoder.decode(ByteBuffer.allocate(1), transform) }, { ports.postProcessor.process(YoloDecodedOutput(emptyList(), transform)) }).forEach { action ->
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
    @Test fun candidateContractDoesNotPromoteItselfAfterComponentsExist() {
        val runtime = Runtime(metadata)
        val contract = Exp02YoloContract.components<Int>(YoloPreprocessor { error("unverified preprocess must not run") })
        assertEquals(ExportContractVerification.NOT_VERIFIED, contract.verification)
        val result = YoloExerciseDetector({ runtime }, contract).detect(0)
        assertEquals(DetectorStatus.CONTRACT_NOT_VERIFIED, result.status)
        assertEquals(0, runtime.calls)
    }
    @Test fun wrongModelIdentityIsDifferentFromMissingModelOrPreprocessing() {
        val result = YoloExerciseDetector<Int>({ throw YoloModelIdentityMismatch() }).initialize()
        assertEquals(DetectorStatus.MODEL_IDENTITY_MISMATCH, result.status)
    }
    @Test fun unsupportedNativeOperationsAreIsolatedAndClassified() {
        val detector = YoloExerciseDetector<Int>({ throw UnsupportedYoloRuntime() })
        assertEquals(DetectorStatus.UNSUPPORTED_RUNTIME, detector.initialize().status)
        assertEquals(ExerciseType.UNKNOWN, detector.detect(0).exerciseType)
        assertTrue(YoloRuntimeCompatibility.isUnsupported(IllegalArgumentException("Didn't find op for builtin opcode synthetic")))
        assertTrue(YoloRuntimeCompatibility.isUnsupported(RuntimeException("initialization", IllegalStateException("unsupported op"))))
        assertFalse(YoloRuntimeCompatibility.isUnsupported(IllegalArgumentException("invalid image dimensions")))
    }
    @Test fun unsupportedRuntimeDuringRunIsDifferentFromDecoderFailure() {
        val runtime = object : YoloTensorRuntime {
            override fun metadata() = this@YoloContractTest.metadata
            override fun run(input: ByteBuffer, output: ByteBuffer) { throw UnsupportedYoloRuntime() }
            override fun close() = Unit
        }
        assertEquals(DetectorStatus.UNSUPPORTED_RUNTIME, YoloExerciseDetector({ runtime }, fakeContract()).detect(0).status)
    }
    @Test fun invalidDecoderOutputFailsClosedWithSpecificStatus() {
        val runtime = Runtime(metadata)
        val contract = fakeContract().copy(decoder = YoloOutputDecoder { _, _ -> throw InvalidYoloOutput() })
        assertEquals(DetectorStatus.INVALID_OUTPUT, YoloExerciseDetector({ runtime }, contract).detect(0).status)
        assertEquals(1, runtime.calls); assertEquals(1, runtime.closes)
    }
    @Test fun wrongNativeInputByteOrderCannotReachRuntime() {
        val runtime = Runtime(metadata)
        val otherOrder = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
        val contract = fakeContract().copy(preprocessor = YoloPreprocessor {
            YoloPreparedInput(ByteBuffer.allocateDirect(4 * 3 * 640 * 640).order(otherOrder), transform)
        })
        assertEquals(DetectorStatus.INFERENCE_FAILED, YoloExerciseDetector({ runtime }, contract).detect(0).status)
        assertEquals(0, runtime.calls)
    }
    @Test fun modelIdentityRejectsWrongLengthAndHashWithoutChangingBufferPosition() {
        listOf(ByteBuffer.allocate(1), ByteBuffer.allocate(YoloModelPolicy.CANONICAL_MODEL_BYTES.toInt())).forEach { buffer ->
            try { YoloModelIdentity.validate(buffer); fail("Wrong model accepted") }
            catch (_: YoloModelIdentityMismatch) { assertEquals(0, buffer.position()) }
        }
    }
    @Test fun finiteCoordinatesWithOverflowingAreaAreNotValidDetections() {
        assertFalse(DetectionBox(-3.2e20f, -3.2e20f, 3.2e20f, 3.2e20f).isValid)
        val raw = ByteBuffer.allocateDirect(4 * 6 * 8400).order(ByteOrder.nativeOrder())
        listOf(.5f, .5f, 1e18f, 1e18f, .9f, .1f).forEachIndexed { channel, value -> raw.putFloat(channel * 8400 * 4, value) }
        try { Exp02OutputDecoder().decode(raw, transform); fail("Overflowing box accepted") }
        catch (_: InvalidYoloOutput) { /* All coordinates are finite, but their area is not. */ }
    }
    @Test fun finiteIndividualAreasDoNotOverflowTheirIouUnionIntoZeroOverlap() {
        val box = DetectionBox(0f, 0f, 1.4e19f, 1.4e19f)
        assertTrue(box.isValid)
        assertEquals(1f, Exp02PostProcessor.iou(box, box), 0f)
    }
}
