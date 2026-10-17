package com.example.arptapp.ui.camera.yolo.validation

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.example.arptapp.domain.detection.*
import com.example.arptapp.ui.camera.yolo.DebugYoloModelSource
import com.example.arptapp.ui.camera.yolo.Exp02BitmapPreprocessor
import com.example.arptapp.ui.camera.yolo.TfliteYoloRuntime
import com.google.gson.Gson
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * One saved PNG, one native invocation, isolated DEBUG validation scope. Reuses actual candidate
 * production preprocessor/decoder/postprocessor without changing Exp02YoloContract.verification.
 * No observer, stabilizer, session bridge, Room, rep analysis, or camera frame loop participates.
 */
internal class DebugPositiveNativeInference(private val context: Context) {
    private var lastMetadata: YoloTensorMetadata? = null
    private var lastInferenceMs: Double? = null
    private var decodedRgbSha256: String? = null
    var nativeInferenceExecuted: Boolean = false
        private set

    fun resetDiagnostics() {
        lastMetadata = null
        lastInferenceMs = null
        decodedRgbSha256 = null
        nativeInferenceExecuted = false
    }

    fun analyze(candidate: PositiveCandidate, bitmap: Bitmap): PositiveCandidateAnalysis {
        resetDiagnostics()
        decodedRgbSha256 = logicalRgbSha256(bitmap)
        TfliteYoloRuntime(DebugYoloModelSource.open(context)).use { runtime ->
            lastMetadata = runtime.metadata()
            require(lastMetadata!!.matchesExpected()) { "TENSOR_MISMATCH" }
            val prepared = Exp02BitmapPreprocessor().preprocess(bitmap)
            val output = ByteBuffer.allocateDirect(4 * 6 * Exp02DetectionPolicy.ANCHORS).order(ByteOrder.nativeOrder())
            val start = SystemClock.elapsedRealtimeNanos()
            runtime.run(prepared.tensor, output)
            nativeInferenceExecuted = true
            lastInferenceMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
            output.rewind()
            val detections = Exp02PostProcessor().process(Exp02OutputDecoder().decode(output, prepared.transform)).map {
                val box = requireNotNull(it.boundingBox)
                PositiveCandidateDetection(YoloModelPolicy.classMapping.entries.single { entry -> entry.value == it.exerciseType }.key,
                    it.exerciseType.name, requireNotNull(it.confidence), PositiveCandidateBox(box.left, box.top, box.right, box.bottom))
            }
            return PositiveCandidateAnalysis(candidate, if (detections.isEmpty()) "NO_DETECTION" else "POSITIVE_CANDIDATE",
                true, detections, "검증 전용 · ${detections.size}개 검출 · NOT_VERIFIED 유지")
        }
    }

    fun json(candidate: PositiveCandidate, result: PositiveCandidateAnalysis): String = Gson().toJson(mapOf(
        "schemaVersion" to 1, "validationPurpose" to "EXPLICIT_DEBUG_POSITIVE_CANDIDATE",
        "modelId" to YoloModelPolicy.MODEL_ID, "modelSha256" to YoloModelPolicy.CANONICAL_SHA256,
        "image" to "${candidate.id}.png", "imageSha256" to candidate.sha256,
        "sourceImageSha256" to candidate.sha256, "decodedRgbSha256" to decodedRgbSha256,
        "width" to candidate.width, "height" to candidate.height,
        "rasterProvenance" to "MANUALLY_REQUESTED_NORMALIZED_CAMERA_BITMAP_LOSSLESS_OPAQUE_PNG_NO_EXTRA_RESIZE_OR_MIRROR",
        "productionContract" to Exp02YoloContract.verification.name, "status" to result.status,
        "androidNativeInferenceExecuted" to result.nativeInferenceExecuted,
        "nativeInferenceCount" to if (result.nativeInferenceExecuted) 1 else 0,
        "tensorMetadata" to lastMetadata, "inferenceMs" to lastInferenceMs,
        "thresholds" to mapOf("confidence" to Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_RAW_CONFIDENCE,
            "iou" to Exp02DetectionPolicy.IOU_THRESHOLD, "maxDetections" to Exp02DetectionPolicy.MAX_DETECTIONS),
        "detectionsAfterNms" to result.detections.size, "classOrdering" to result.detections.map { it.classId },
        "detections" to result.detections,
        "semanticCorrectness" to "NOT_LABELED_NOT_EVALUATED",
        "predictionUsedForSession" to false, "contractPromotionPerformed" to false))

    companion object {
        /** Logical RGB rows, not platform Bitmap byte order; detects any Python PNG decode drift. */
        internal fun logicalRgbSha256(bitmap: Bitmap): String {
            DebugPositiveCandidateStore.requireDimensions(bitmap.width, bitmap.height)
            val digest = MessageDigest.getInstance("SHA-256")
            val pixels = IntArray(bitmap.width)
            val bytes = ByteArray(bitmap.width * 3)
            for (y in 0 until bitmap.height) {
                bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                for (x in pixels.indices) {
                    bytes[x * 3] = (pixels[x] ushr 16).toByte()
                    bytes[x * 3 + 1] = (pixels[x] ushr 8).toByte()
                    bytes[x * 3 + 2] = pixels[x].toByte()
                }
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        }

        fun failureCode(error: Throwable): String = when (error) {
            is MissingYoloModel -> "MODEL_MISSING"
            is YoloModelIdentityMismatch -> "MODEL_IDENTITY_MISMATCH"
            is UnsupportedYoloRuntime, is LinkageError -> "UNSUPPORTED_RUNTIME"
            is InvalidYoloOutput -> "INVALID_MODEL_OUTPUT"
            is Exception -> if (error.message == "TENSOR_MISMATCH") "TENSOR_MISMATCH" else "PRIVATE_VALIDATION_FAILED"
            // An OOM/ThreadDeath/other VM failure must not be downgraded to a benign detector state.
            else -> throw error
        }
    }
}
