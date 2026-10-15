package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import java.nio.ByteBuffer

/** Only file identity / tensors / class mapping are established. No pixel or decoder assumptions. */
object YoloModelPolicy {
    const val MODEL_ID = "yolo26n_exp02_fp32"
    const val FILE_NAME = "airptcoach_yolo26n_exp02_fp32.tflite"
    const val MAX_MODEL_BYTES = 16L * 1024 * 1024
    val inputShape: List<Int> get() = listOf(1, 3, 640, 640)
    val outputShape: List<Int> get() = listOf(1, 6, 8400)
    val classMapping = mapOf(0 to ExerciseType.SQUAT, 1 to ExerciseType.SHOULDER_PRESS)
    val supported = classMapping.values.toSet()
}
data class TensorSpec(val shape: List<Int>, val dtype: String)
data class YoloTensorMetadata(val inputs: List<TensorSpec>, val outputs: List<TensorSpec>) {
    fun matchesExpected(): Boolean = inputs == listOf(TensorSpec(YoloModelPolicy.inputShape, "FLOAT32")) &&
        outputs == listOf(TensorSpec(YoloModelPolicy.outputShape, "FLOAT32"))
}

/** Interpreter is created, inspected, invoked and closed by ONE worker only. */
interface YoloTensorRuntime : AutoCloseable {
    fun metadata(): YoloTensorMetadata
    fun run(input: ByteBuffer, output: ByteBuffer)
}
class MissingYoloModel : Exception()
class ExportContractNotVerified : IllegalStateException("NOT_VERIFIED: TODO(EXP02_EXPORT_CONTRACT)")

/** Future preprocessor owns resize/color/layout/normalization AND the inverse box transform. */
interface YoloFrameTransform
data class YoloPreparedInput(val tensor: ByteBuffer, val transform: YoloFrameTransform)
fun interface YoloPreprocessor<F> { fun preprocess(frame: F): YoloPreparedInput }
fun interface YoloOutputDecoder { fun decode(output: ByteBuffer, transform: YoloFrameTransform): List<ExerciseDetection> }
/** Future confidence filtering / NMS, ONLY after the original export/test contract is verified. */
fun interface YoloPostProcessor { fun process(decoded: List<ExerciseDetection>): List<ExerciseDetection> }

enum class ExportContractVerification { NOT_VERIFIED, VERIFIED }
data class YoloContractComponents<F>(
    val verification: ExportContractVerification,
    val preprocessor: YoloPreprocessor<F>, val decoder: YoloOutputDecoder, val postProcessor: YoloPostProcessor,
    val stabilization: YoloStabilizationPolicy
)

/** The shipping dependency graph uses ONLY these fail-closed ports. A flag cannot verify a contract. */
object UnverifiedYoloContract {
    fun <F> components() = YoloContractComponents<F>(ExportContractVerification.NOT_VERIFIED,
        YoloPreprocessor { throw ExportContractNotVerified() },
        YoloOutputDecoder { _, _ -> throw ExportContractNotVerified() },
        YoloPostProcessor { throw ExportContractNotVerified() }, YoloStabilizationPolicy(minimumConfidence = null))
}
