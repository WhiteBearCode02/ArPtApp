package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class YoloRuntimePolicyTest {
    @Test fun releaseCannotEnableEvenWhenRequestedAndModelReady() {
        assertFalse(YoloRuntimePolicy.forBuild(false, true).shadowRuntimeEnabled)
        assertFalse(YoloRuntimePolicy.forBuild(false, true).predictionAllowed(ExportContractVerification.VERIFIED, DetectorStatus.READY))
    }

    @Test fun allThreeIndependentConditionsAreRequired() {
        for (contract in ExportContractVerification.entries) for (status in DetectorStatus.entries) for (enabled in listOf(false, true)) {
            val expected = contract == ExportContractVerification.VERIFIED && status == DetectorStatus.READY && enabled
            assertEquals(expected, YoloRuntimePolicy.forBuild(true, enabled).predictionAllowed(contract, status))
            assertEquals(expected, ExerciseDetectionResult(status, DetectionSource.YOLO,
                contractVerification = contract, shadowRuntimeEnabled = enabled).predictionAllowed)
        }
    }

    @Test fun detectorGateBlocksPreprocessingAndNativeButDoesNotForgeModelFailure() {
        for (verified in listOf(false, true)) for (enabled in listOf(false, true)) {
            val runs = AtomicInteger(); val preprocesses = AtomicInteger()
            val runtime = object : YoloTensorRuntime {
                override fun metadata() = YoloTensorMetadata(listOf(TensorSpec(YoloModelPolicy.inputShape, "FLOAT32")),
                    listOf(TensorSpec(YoloModelPolicy.outputShape, "FLOAT32")))
                override fun run(input: ByteBuffer, output: ByteBuffer) { runs.incrementAndGet() }
                override fun close() = Unit
            }
            val contract = YoloContractComponents<Int>(if (verified) ExportContractVerification.VERIFIED else ExportContractVerification.NOT_VERIFIED,
                YoloPreprocessor { preprocesses.incrementAndGet(); YoloPreparedInput(
                    ByteBuffer.allocateDirect(4 * 3 * 640 * 640).order(ByteOrder.nativeOrder()), Exp02FrameTransform.create(640, 640)) },
                YoloOutputDecoder { _, transform -> YoloDecodedOutput(emptyList(), transform) },
                YoloPostProcessor { emptyList() }, YoloStabilizationPolicy(.60f))
            YoloExerciseDetector({ runtime }, contract, runtimePolicy = YoloRuntimePolicy.forBuild(true, enabled)).use {
                val result = it.detect(0)
                assertEquals(verified && enabled, result.predictionAllowed)
                assertEquals(if (verified) DetectorStatus.READY else DetectorStatus.CONTRACT_NOT_VERIFIED, result.status)
                assertEquals(if (verified && enabled) 1 else 0, runs.get())
                assertEquals(runs.get(), preprocesses.get())
            }
        }
    }

    @Test fun runnerDoesNotCopyVerifiedReadyFramesWhenIndependentGateIsOff() {
        val results = LinkedBlockingQueue<YoloShadowSnapshot>(); val copies = AtomicInteger(); val runs = AtomicInteger()
        val detector = object : ExerciseDetector<Int> {
            override fun initialize() = ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
                contractVerification = ExportContractVerification.VERIFIED, shadowRuntimeEnabled = false)
            override fun detect(frame: Int) = initialize().also { runs.incrementAndGet() }
            override fun close() = Unit
        }
        YoloShadowRunner(detector, YoloExerciseStabilizer(YoloStabilizationPolicy(.60f)),
            { copies.incrementAndGet(); it }, {}, results::offer).use { runner ->
            runner.start()
            assertEquals(DetectorStatus.READY, requireNotNull(results.poll(5, TimeUnit.SECONDS)).result.status)
            repeat(8) { assertFalse(runner.trySubmit(it, 1000L + it * 250)) }
            assertEquals(0, copies.get()); assertEquals(0, runs.get())
        }
    }

    @Test fun formatterSeparatesContractRuntimePrimaryAndAvailability() {
        val text = DetectorComparisonFormatter.format(DetectorComparison(YoloShadowSnapshot(ExerciseDetectionResult(
            DetectorStatus.READY, DetectionSource.YOLO, contractVerification = ExportContractVerification.VERIFIED)),
            ExerciseType.SQUAT, ExerciseType.SQUAT, com.example.arptapp.domain.analyzer.CameraView.SIDE, 1, 0))
        assertTrue(text.contains("exportContract=VERIFIED")); assertTrue(text.contains("shadow=DISABLED"))
        assertTrue(text.contains("primary=LANDMARK")); assertTrue(text.contains("prediction 비활성"))
    }

    @Test fun contradictoryShadowDiagnosticsCannotMutatePrimaryRepsScoreOrReport() {
        val primary = com.example.arptapp.viewmodel.MainViewModel()
        primary.observeDetectedExercise(ExerciseType.SQUAT)
        primary.addRepRecord(1, 95.0, .1f, poseScore = 80f)
        val records = primary.getRepRecords()
        val report = primary.generateFinalReport("SQUAT")
        val bridge = YoloSessionDiagnosticsBridge(monotonicMs = { 1500 })
        bridge.begin(1000, 100, com.example.arptapp.domain.analyzer.CameraView.SIDE)
        bridge.updateContext(primary.sessionExerciseType, 0)
        val context = requireNotNull(bridge.captureContext())
        val opposite = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
            ExerciseDetection(ExerciseType.SHOULDER_PRESS, .99f, DetectionSource.YOLO), timings = DetectionTimings(1, 2, 3, 6),
            contractVerification = ExportContractVerification.VERIFIED, shadowRuntimeEnabled = true), ExerciseType.SHOULDER_PRESS)
        repeat(4) {
            bridge.onFrameSeen(context); bridge.onAdmission(context); bridge.onResult(context, opposite, true)
        }
        val diagnostic = requireNotNull(bridge.finish(2000, 1600, primary.sessionExerciseType, 0))
        assertEquals(4L, diagnostic.mismatchingStableFrames)
        assertEquals(ExerciseType.SQUAT, primary.sessionExerciseType)
        assertEquals(records, primary.getRepRecords())
        assertEquals(report, primary.generateFinalReport("SQUAT"))
    }
}
