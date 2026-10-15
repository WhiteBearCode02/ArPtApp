package com.example.arptapp.domain.detection

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Fail-closed lifecycle, independent of Android/native code for failure-contract tests. */
class YoloExerciseDetector<F>(
    private val runtimeFactory: () -> YoloTensorRuntime,
    private val contract: YoloContractComponents<F> = UnverifiedYoloContract.components(),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 }
) : ExerciseDetector<F> {
    private var runtime: YoloTensorRuntime? = null
    private var result = state(DetectorStatus.INITIALIZING)
    private var output: ByteBuffer? = null
    override fun initialize(): ExerciseDetectionResult {
        if (result.status != DetectorStatus.INITIALIZING) return result
        try {
            runtime = runtimeFactory()
            if (runtime?.metadata()?.matchesExpected() != true) return fail(DetectorStatus.TENSOR_MISMATCH)
            if (contract.verification != ExportContractVerification.VERIFIED || contract.stabilization.minimumConfidence == null)
                return fail(DetectorStatus.CONTRACT_NOT_VERIFIED)
            output = ByteBuffer.allocateDirect(YoloModelPolicy.outputShape.fold(4) { a, n -> a * n }).order(ByteOrder.nativeOrder())
            result = state(DetectorStatus.READY)
        } catch (_: MissingYoloModel) { return fail(DetectorStatus.MODEL_MISSING) }
        catch (_: Exception) { return fail(DetectorStatus.INITIALIZATION_FAILED) }
        catch (_: LinkageError) { return fail(DetectorStatus.INITIALIZATION_FAILED) }
        return result
    }
    override fun detect(frame: F): ExerciseDetectionResult {
        initialize()
        if (result.status != DetectorStatus.READY) return result
        val start = clockMs()
        return try {
            val prepared = contract.preprocessor.preprocess(frame)
            val inputBytes = YoloModelPolicy.inputShape.fold(4) { a, n -> a * n }
            if (!prepared.tensor.isDirect || prepared.tensor.remaining() != inputBytes) return fail(DetectorStatus.INFERENCE_FAILED)
            val preprocessed = clockMs()
            val buffer = output ?: return fail(DetectorStatus.INFERENCE_FAILED)
            buffer.clear()
            runtime?.run(prepared.tensor, buffer) ?: return fail(DetectorStatus.INFERENCE_FAILED)
            val inferred = clockMs()
            buffer.rewind()
            val decoded = contract.decoder.decode(buffer.asReadOnlyBuffer().order(ByteOrder.nativeOrder()), prepared.transform)
            val detections = contract.postProcessor.process(decoded)
            if (detections.size > 8400 || detections.any { it.source != DetectionSource.YOLO || it.exerciseType !in YoloModelPolicy.supported ||
                    it.confidence?.let { c -> c.isFinite() && c in 0f..1f } != true || it.boundingBox?.isValid == false })
                return fail(DetectorStatus.INVALID_OUTPUT)
            val end = clockMs()
            result = ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
                MainExerciseDetectionSelector.select(detections), detections.size,
                DetectionTimings((preprocessed-start).coerceAtLeast(0), (inferred-preprocessed).coerceAtLeast(0),
                    (end-inferred).coerceAtLeast(0), (end-start).coerceAtLeast(0)))
            result
        } catch (_: ExportContractNotVerified) { fail(DetectorStatus.CONTRACT_NOT_VERIFIED) }
        catch (_: Exception) { fail(DetectorStatus.INFERENCE_FAILED) }
        catch (_: LinkageError) { fail(DetectorStatus.INFERENCE_FAILED) }
    }
    private fun state(status: DetectorStatus) = ExerciseDetectionResult(status, DetectionSource.YOLO)
    private fun fail(status: DetectorStatus): ExerciseDetectionResult {
        // Called only on the owner worker, never concurrently with Interpreter.run.
        runCatching { runtime?.close() }; runtime = null; output = null
        result = state(status)
        return result
    }
    override fun close() { fail(DetectorStatus.CLOSED) }
}
