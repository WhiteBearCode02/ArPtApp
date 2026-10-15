package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import java.nio.ByteBuffer

/** Canonical EXP02 identity; a different file must not inherit this model's source-derived contract. */
object YoloModelPolicy {
    const val MODEL_ID = "yolo26n_exp02_fp32"
    const val FILE_NAME = "airptcoach_yolo26n_exp02_fp32.tflite"
    const val MAX_MODEL_BYTES = 16L * 1024 * 1024
    const val CANONICAL_MODEL_BYTES = 9894030L
    const val CANONICAL_SHA256 = "464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749"
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
class YoloModelIdentityMismatch : Exception()
class UnsupportedYoloRuntime : Exception()
class InvalidYoloOutput : IllegalArgumentException("Invalid detector output")
class ExportContractNotVerified : IllegalStateException("NOT_VERIFIED: TODO(EXP02_EXPORT_CONTRACT)")

/** Geometry is carried through decoding; clipping/rescaling MUST happen after model-space NMS. */
interface YoloFrameTransform {
    val originalWidth: Int
    val originalHeight: Int
    val modelWidth: Int
    val modelHeight: Int
    fun originalToModel(box: DetectionBox): DetectionBox
    fun modelToOriginal(box: DetectionBox): DetectionBox
}
data class YoloPreparedInput(val tensor: ByteBuffer, val transform: YoloFrameTransform)
/** Typed intermediate prevents accidental NMS on prematurely clipped source-frame boxes. */
data class YoloDecodedCandidate(val anchorIndex: Int, val classId: Int, val confidence: Float, val modelBox: DetectionBox)
data class YoloDecodedOutput(val candidates: List<YoloDecodedCandidate>, val transform: YoloFrameTransform)
fun interface YoloPreprocessor<F> { fun preprocess(frame: F): YoloPreparedInput }
fun interface YoloOutputDecoder { fun decode(output: ByteBuffer, transform: YoloFrameTransform): YoloDecodedOutput }
/** Confidence filtering / NMS port; production inference still requires a VERIFIED contract. */
fun interface YoloPostProcessor { fun process(decoded: YoloDecodedOutput): List<ExerciseDetection> }

enum class ExportContractVerification { NOT_VERIFIED, VERIFIED }
data class YoloContractComponents<F>(
    val verification: ExportContractVerification,
    val preprocessor: YoloPreprocessor<F>, val decoder: YoloOutputDecoder, val postProcessor: YoloPostProcessor,
    val stabilization: YoloStabilizationPolicy
)

/** Preserved fail-closed ports for unknown future exports. A flag cannot verify a contract. */
object UnverifiedYoloContract {
    fun <F> components() = YoloContractComponents<F>(ExportContractVerification.NOT_VERIFIED,
        YoloPreprocessor { throw ExportContractNotVerified() },
        YoloOutputDecoder { _, _ -> throw ExportContractNotVerified() },
        YoloPostProcessor { throw ExportContractNotVerified() }, YoloStabilizationPolicy(minimumConfidence = null))
}
