package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*
import com.google.gson.Gson
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/** Test-only, hash-bound private fixture API. No Intent, preference, asset or network gate exists. */
internal class Exp04GoldenFixtures {
    data class Model(val id: String, val filename: String, val bytes: Long, val sha256: String)
    data class Thresholds(val confidence: Float, val iou: Float, val maxDetections: Int)
    data class Tolerances(val confidence: Float, val bboxPixels: Float, val detectionCount: Int, val classMatch: String)
    data class Payload(val file: String, val bytes: Long, val sha256: String)
    data class Box(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    data class Detection(val classId: Int, val className: String, val confidence: Float, val bbox: Box) {
        fun golden() = YoloGoldenDetection(classId, confidence, DetectionBox(bbox.x1, bbox.y1, bbox.x2, bbox.y2))
    }
    data class Geometry(val logicalColor: String, val layout: String, val dtype: String, val normalizationDivisor: Int,
        val paddingValue: Int, val centered: Boolean, val auto: Boolean, val resizedWidth: Int, val resizedHeight: Int,
        val padLeft: Int, val padTop: Int, val padRight: Int, val padBottom: Int)
    data class Sample(val id: String, val exercise: String, val cameraView: String, val width: Int, val height: Int,
        val rightsCleared: Boolean, val rightsBasis: String, val sourceImageSha256: String,
        val payloads: Map<String, Payload>, val preprocessing: Geometry,
        val detections: List<Detection>, val classOrdering: List<Int>)
    data class Reference(val schemaVersion: Int, val validationPurpose: String, val model: Model,
        val ultralyticsVersion: String, val wheelSha256: String, val sourceSha256: Map<String, String>,
        val classes: List<String>, val thresholds: Thresholds, val tolerances: Tolerances,
        val preprocessingAcceptance: String, val tensorByteOrder: String, val modelInferenceExecuted: Boolean,
        val nativeInferenceCount: Int, val androidNativeInferenceExecuted: Boolean,
        val exportContractVerified: Boolean, val contractPromotionPerformed: Boolean, val samples: List<Sample>)

    val profile = YoloModelProfiles.EXP04
    val identity = profile.descriptor
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    val referenceHash: String
    val directory: File
    val reference: Reference
    private val gson = Gson()

    init {
        requireSandbox()
        val args = InstrumentationRegistry.getArguments()
        val run = requireNotNull(args.getString("exp04GoldenRun"))
        require(run.matches(SAFE_ID))
        referenceHash = requireNotNull(args.getString("exp04ReferenceSha256"))
        require(referenceHash.matches(SHA))
        directory = File(context.noBackupFilesDir.canonicalFile, "yolo/exp04-actual-golden/runs/$run")
        require(directory.canonicalFile == directory.absoluteFile && directory.isDirectory)
        val file = File(directory, "reference.json")
        require(file.canonicalFile == file.absoluteFile && file.isFile && file.length() in 1..2_097_152)
        val bytes = file.readBytes()
        require(hash(ByteBuffer.wrap(bytes)) == referenceHash)
        reference = gson.fromJson(bytes.toString(Charsets.UTF_8), Reference::class.java)
        validateReference()
    }

    private fun validateReference() = with(reference) {
        require(schemaVersion == 1 && validationPurpose == "EXP04_ACTUAL_IMAGE_GOLDEN")
        require(model == Model(identity.modelId, identity.fileName, identity.bytes, identity.sha256))
        require(ultralyticsVersion == "8.4.202" && wheelSha256 == "ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4")
        require(sourceSha256 == mapOf(
            "ultralytics/data/augment.py" to "414e49b76e5babfa118b88de60558e939fc436945cdf7a7ec4c09b0320a7036e",
            "ultralytics/engine/predictor.py" to "be124f7054a64139698434fba742f2d0a46dc73e32215ee0c750e6d429e423ec",
            "ultralytics/nn/backends/litert.py" to "e22fcde664c4e849ae26556a72cf77e890fdaf82031b1b239ce4571b4bace39f",
            "ultralytics/utils/ops.py" to "7b15cd980abbd578a8be91661c2399b2cbdea0207f1e61b0fb74cfac659bed77",
            "ultralytics/utils/nms.py" to "386cd0e3e38fddef5d434fbcfd58264bedfa37295534e9a2b7174a3436d4db62"))
        require(classes == profile.classMapping.values.map { it.name })
        require(modelInferenceExecuted && nativeInferenceCount == samples.size && !androidNativeInferenceExecuted)
        require(!exportContractVerified && !contractPromotionPerformed)
        require(preprocessingAcceptance == "EXACT_TENSOR_BYTES_SHA256" && tensorByteOrder == "LITTLE_ENDIAN")
        require(thresholds == Thresholds(.10f, .70f, 300) && tolerances == Tolerances(.001f, 2f, 0, "EXACT"))
        require(samples.size in 7..64 && samples.map { it.id }.distinct().size == samples.size)
        require(samples.map { it.exercise }.toSet() == classes.toSet())
        samples.forEach { sample ->
            require(sample.id.matches(SAFE_ID) && sample.rightsCleared && sample.rightsBasis.isNotBlank())
            require(sample.sourceImageSha256.matches(SHA) && sample.cameraView in setOf("FRONT", "SIDE", "DIAGONAL"))
            Exp02FrameTransform.create(sample.width, sample.height) // allocation bound, not proof of interpolation parity
            require(sample.payloads.keys == setOf(".rgb", ".input.f32", ".output.f32"))
            for ((suffix, size) in listOf(".rgb" to sample.width.toLong() * sample.height * 3,
                ".input.f32" to profile.inputBytes.toLong(), ".output.f32" to profile.outputBytes.toLong())) {
                val payload = sample.payloads.getValue(suffix)
                require(payload.file == sample.id + suffix && payload.bytes == size && payload.sha256.matches(SHA))
            }
            require(sample.classOrdering == sample.detections.map { it.classId })
            require(sample.detections.all { profile.classMapping[it.classId]?.name == it.className })
            val detections = sample.detections.map { it.golden() }
            require(YoloActualGoldenComparator.compare(detections, detections, profile = profile).passed)
        }
    }

    fun geometry(sample: Sample, transform: Exp02FrameTransform) = with(sample.preprocessing) {
        require(logicalColor == "RGB" && layout == "BCHW" && dtype == "FLOAT32" && normalizationDivisor == 255)
        require(paddingValue == 114 && centered && !auto)
        require(resizedWidth == transform.resizedWidth && resizedHeight == transform.resizedHeight &&
            padLeft == transform.padX && padTop == transform.padY &&
            padRight == 640 - resizedWidth - padLeft && padBottom == 640 - resizedHeight - padTop)
    }

    fun payload(sample: Sample, suffix: String): ByteBuffer {
        val info = sample.payloads.getValue(suffix)
        val file = File(directory, info.file)
        require(file.canonicalFile == file.absoluteFile && file.isFile && file.length() == info.bytes)
        return ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN).also { require(hash(it) == info.sha256) }
    }

    fun bitmap(sample: Sample): Bitmap {
        val rgb = payload(sample, ".rgb")
        val pixels = IntArray(sample.width * sample.height) {
            (0xff shl 24) or ((rgb.get().toInt() and 255) shl 16) or
                ((rgb.get().toInt() and 255) shl 8) or (rgb.get().toInt() and 255)
        }
        return Bitmap.createBitmap(pixels, sample.width, sample.height, Bitmap.Config.ARGB_8888)
    }

    fun writeNewResult(name: String, value: Any) {
        require(name in setOf("android-preprocessing.json", "android-result.json"))
        val file = File(directory, name)
        require(file.canonicalFile == file.absoluteFile && !file.exists() && file.createNewFile())
        file.setReadable(false, false); file.setWritable(false, false)
        require(file.setReadable(true, true) && file.setWritable(true, true))
        val json = gson.toJson(value)
        require(json.toByteArray(Charsets.UTF_8).size <= 2_097_152)
        file.writeText(json, Charsets.UTF_8)
    }

    companion object {
        private val SAFE_ID = Regex("[a-z0-9][a-z0-9_-]{0,63}")
        private val SHA = Regex("[a-f0-9]{64}")
        fun requireSandbox() {
            check(BuildConfig.DEBUG && BuildConfig.YOLO_AB_COMPARISON && !BuildConfig.YOLO_PRIVATE_BUNDLED)
            check(BuildConfig.YOLO_MODEL_VARIANT == "EXP04" && BuildConfig.APPLICATION_ID == "com.example.arptapp.exp04")
        }
        fun hash(bytes: ByteBuffer): String = MessageDigest.getInstance("SHA-256").apply { update(bytes.asReadOnlyBuffer()) }
            .digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
