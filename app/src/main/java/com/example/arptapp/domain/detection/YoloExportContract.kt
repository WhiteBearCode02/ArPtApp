package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import java.nio.ByteBuffer

/** Exact known identities for history and explicit legacy validation, never wildcard/hash-only trust. */
data class YoloModelDescriptor(val modelId: String, val fileName: String, val bytes: Long, val sha256: String)

object KnownYoloModels {
    val EXP02 = YoloModelDescriptor("yolo26n_exp02_fp32", "airptcoach_yolo26n_exp02_fp32.tflite", 9894030L,
        "464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749")
    val EXP03 = YoloModelDescriptor("yolo26n_exp03_final_fp32", "airptcoach_yolo26n_exp03_final_fp32.tflite", 9894032L,
        "3d2b6bc537d8aef5a19b671226d73c98b49411574a193be72e225086202d1167")
    // Exact local bytes/hash checked on 2026-11-12; NOT an Android contract promotion.
    val EXP04 = YoloModelDescriptor("yolo26n_exp04_7class_fp32", "airptcoach_yolo26n_exp04_7class_fp32.tflite", 9898949L,
        "3e613e94fc1ef3aebbeba309d1b31d7ba6fc71a19274c9b2946793fe493eb84d")
    private val known = listOf(EXP02, EXP03, EXP04)
    fun find(modelId: String): YoloModelDescriptor? = known.firstOrNull { it.modelId == modelId }
    fun contains(identity: YoloModelDescriptor): Boolean = identity in known
    fun matches(modelId: String, sha256: String): Boolean = known.any { it.modelId == modelId && it.sha256 == sha256 }
}

/** Selected final app export. "Production" selects identity, NOT Primary authority or redistribution rights. */
object YoloModelPolicy {
    const val MODEL_VERSION = "EXP02"
    const val MODEL_ID = "yolo26n_exp02_fp32"
    const val FILE_NAME = "airptcoach_yolo26n_exp02_fp32.tflite"
    const val MAX_MODEL_BYTES = 16L * 1024 * 1024
    const val CANONICAL_MODEL_BYTES = 9894030L
    const val CANONICAL_SHA256 = "464cafe87c3d6e04ba71951e73efab8690367f77fb224105d4b1e50ab3d33749"
    val identity get() = KnownYoloModels.EXP02
    val inputShape: List<Int> get() = listOf(1, 3, 640, 640)
    val outputShape: List<Int> get() = listOf(1, 6, 8400)
    val classMapping = mapOf(0 to ExerciseType.SQUAT, 1 to ExerciseType.SHOULDER_PRESS)
    val supported = classMapping.values.toSet()
}
data class TensorSpec(val shape: List<Int>, val dtype: String)
data class YoloTensorMetadata(val inputs: List<TensorSpec>, val outputs: List<TensorSpec>) {
    fun matchesExpected(profile: YoloModelProfile = YoloModelProfiles.EXP02): Boolean =
        inputs == listOf(TensorSpec(profile.inputShape, "FLOAT32")) &&
            outputs == listOf(TensorSpec(profile.outputShape, "FLOAT32"))
}

/** Interpreter is created, inspected, invoked and closed by ONE worker only. */
interface YoloTensorRuntime : AutoCloseable {
    /** Real native adapters report the identity whose bytes they validated before construction. */
    val modelId: String get() = YoloModelPolicy.MODEL_ID
    fun metadata(): YoloTensorMetadata
    fun run(input: ByteBuffer, output: ByteBuffer)
}
class MissingYoloModel : Exception()
class YoloModelIdentityMismatch : Exception()
class UnsupportedYoloRuntime : Exception()
class InvalidYoloOutput : IllegalArgumentException("Invalid detector output")
class ExportContractNotVerified : IllegalStateException("NOT_VERIFIED: model-specific Android export contract required")

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
    val stabilization: YoloStabilizationPolicy,
    val modelId: String = YoloModelPolicy.MODEL_ID
) {
    /** Derived from exact registered identity, never a mutable global/caller-supplied shape. */
    val profile: YoloModelProfile get() = YoloModelProfiles.requireModel(modelId)
}

/** Preserved fail-closed ports for unknown future exports. A flag cannot verify a contract. */
object UnverifiedYoloContract {
    fun <F> components() = YoloContractComponents<F>(ExportContractVerification.NOT_VERIFIED,
        YoloPreprocessor { throw ExportContractNotVerified() },
        YoloOutputDecoder { _, _ -> throw ExportContractNotVerified() },
        YoloPostProcessor { throw ExportContractNotVerified() }, YoloStabilizationPolicy(minimumConfidence = null))
}
