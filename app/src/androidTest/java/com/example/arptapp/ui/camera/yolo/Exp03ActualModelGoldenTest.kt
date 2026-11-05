package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Explicit DEBUG instrumentation, not a runtime gate/Intent/preference bypass.
 * Reuses production components and unchanged comparison limits. Actual source labels are sanity
 * metadata only. Empty/misclassified outputs are preserved, never removed to improve the result.
 */
@RunWith(AndroidJUnit4::class)
class Exp03ActualModelGoldenTest {
    private val gson = Gson()
    private val identity = KnownYoloModels.EXP03
    private data class Model(val id: String, val filename: String, val bytes: Long, val sha256: String)
    private data class Thresholds(val confidence: Float, val iou: Float, val maxDetections: Int)
    private data class Tolerances(val confidence: Float, val bboxPixels: Float, val detectionCount: Int, val classMatch: String)
    private data class Box(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    private data class Expected(val classId: Int, val confidence: Float, val bbox: Box)
    private data class Preprocessing(val logicalColor: String, val layout: String, val dtype: String,
        val normalizationDivisor: Int, val paddingValue: Int, val centered: Boolean, val auto: Boolean,
        val resizedWidth: Int, val resizedHeight: Int, val padLeft: Int, val padTop: Int,
        val padRight: Int, val padBottom: Int, val gainX: Double, val gainY: Double)
    private data class Sample(val id: String, val exercise: String, val cameraView: String,
        val rightsCleared: Boolean, val rightsBasis: String, val sourceImageSha256: String,
        val width: Int, val height: Int, val rgbFile: String, val rgbSha256: String,
        val inputTensorFile: String, val inputTensorSha256: String, val inputTensorByteOrder: String,
        val nativeOutputFile: String, val nativeOutputSha256: String, val nativeOutputByteOrder: String,
        val modelInferenceExecuted: Boolean, val preprocessing: Preprocessing, val detections: List<Expected>)
    private data class Reference(val schemaVersion: Int, val validationPurpose: String, val model: Model,
        val ultralyticsVersion: String, val wheelSha256: String, val sourceSha256: Map<String, String>,
        val modelInferenceExecuted: Boolean, val nativeInferenceCount: Int, val exportContractVerified: Boolean,
        val thresholds: Thresholds, val tolerances: Tolerances, val samples: List<Sample>)

    @Test fun actualExp03MatchesExactVersionPythonReference() {
        check(BuildConfig.DEBUG)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val args = InstrumentationRegistry.getArguments()
        val runId = requireNotNull(args.getString("exp03GoldenRun"))
        require(runId.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        val expectedReferenceHash = requireNotNull(args.getString("exp03ReferenceSha256"))
        require(expectedReferenceHash.matches(Regex("[a-f0-9]{64}")))
        val root = context.noBackupFilesDir.canonicalFile
        val directory = File(root, "yolo/exp03-actual-golden/runs/$runId")
        require(directory.canonicalFile == directory.absoluteFile && directory.isDirectory)
        val resultFile = File(directory, "android-result.json")
        require(!resultFile.exists() && resultFile.canonicalFile.parentFile == directory)
        val rows = mutableListOf<Map<String, Any>>()
        var metadata: YoloTensorMetadata? = null
        var count = 0
        var passed = false
        var failure = "NONE"
        try {
            val referenceFile = File(directory, "reference.json")
            require(referenceFile.canonicalFile == referenceFile.absoluteFile && referenceFile.isFile && referenceFile.length() in 1..2_097_152)
            val referenceBytes = referenceFile.readBytes()
            require(hash(ByteBuffer.wrap(referenceBytes)) == expectedReferenceHash)
            val reference = gson.fromJson(referenceBytes.toString(Charsets.UTF_8), Reference::class.java)
            validateReference(reference)
            TfliteYoloRuntime(DebugYoloModelSource.open(context, KnownYoloModels.EXP03), KnownYoloModels.EXP03).use { runtime ->
                assertEquals(identity.modelId, runtime.modelId)
                metadata = runtime.metadata()
                assertTrue(metadata!!.matchesExpected())
                val preprocessor = YoloBitmapPreprocessor()
                for (sample in reference.samples) {
                    val rgb = readFixture(directory, sample.rgbFile, sample.width.toLong() * sample.height * 3, sample.rgbSha256)
                    val pixels = IntArray(sample.width * sample.height) { index ->
                        val offset = index * 3
                        (0xff shl 24) or ((rgb[offset].toInt() and 255) shl 16) or
                            ((rgb[offset + 1].toInt() and 255) shl 8) or (rgb[offset + 2].toInt() and 255)
                    }
                    val bitmap = Bitmap.createBitmap(pixels, sample.width, sample.height, Bitmap.Config.ARGB_8888)
                    try {
                        val started = SystemClock.elapsedRealtimeNanos()
                        val prepared = preprocessor.preprocess(bitmap)
                        val preprocessingEnd = SystemClock.elapsedRealtimeNanos()
                        val transform = prepared.transform as Exp02FrameTransform
                        requireGeometry(sample, transform)
                        val referenceInput = ByteBuffer.wrap(readFixture(directory, sample.inputTensorFile, 4L * 3 * 640 * 640,
                            sample.inputTensorSha256)).order(ByteOrder.LITTLE_ENDIAN)
                        val referenceRaw = ByteBuffer.wrap(readFixture(directory, sample.nativeOutputFile, 4L * 6 * 8400,
                            sample.nativeOutputSha256)).order(ByteOrder.LITTLE_ENDIAN)
                        val pixelComparison = YoloActualGoldenTensorComparator.compare(referenceInput, prepared.tensor, transform)
                        val expected = sample.detections.map { YoloGoldenDetection(it.classId, it.confidence,
                            DetectionBox(it.bbox.x1, it.bbox.y1, it.bbox.x2, it.bbox.y2)) }
                        val inputHash = hash(prepared.tensor)
                        val nativeStarted = SystemClock.elapsedRealtimeNanos()
                        val output = invoke(runtime, prepared.tensor)
                        count++
                        val nativeEnded = SystemClock.elapsedRealtimeNanos()
                        val actual = decode(output, transform)
                        val comparison = YoloActualGoldenComparator.compare(expected, actual)
                        // Same Python tensor isolates native numeric error from Android RGB interpolation error.
                        val identicalInput = ByteBuffer.allocateDirect(referenceInput.remaining()).order(ByteOrder.nativeOrder())
                        identicalInput.put(referenceInput.duplicate()).rewind()
                        val identicalOutput = invoke(runtime, identicalInput)
                        count++
                        val nativeOnly = YoloActualGoldenComparator.compare(expected, decode(identicalOutput, transform))
                        // Frozen REAL EXP03 raw output isolates decoder/NMS from both preprocessing and native kernels.
                        val frozenRaw = YoloActualGoldenComparator.compare(expected, decode(referenceRaw, transform),
                            YoloGoldenTolerance(confidence = 1e-6f, bboxPixels = 1e-4f))
                        val rawDifference = maximumRawDifference(referenceRaw, identicalOutput)
                        val samplePassed = pixelComparison.passed && comparison.passed && nativeOnly.passed && frozenRaw.passed
                        rows += mapOf("id" to sample.id, "exercise" to sample.exercise, "cameraView" to sample.cameraView,
                            "passed" to samplePassed, "preprocessingComparison" to pixelComparison,
                            "inputTensorSha256" to inputHash, "inputTensorByteIdenticalToPython" to (inputHash == sample.inputTensorSha256),
                            "comparison" to comparison, "samePythonTensorNativeComparison" to nativeOnly,
                            "frozenPythonRawDecodeNmsComparison" to frozenRaw,
                            "samePythonTensorMaximumRawAbsoluteDifference" to rawDifference,
                            "detections" to actual, "referenceDetections" to expected,
                            "classOrdering" to actual.map { it.classId }, "referenceClassOrdering" to expected.map { it.classId },
                            "semanticMatch" to (actual.firstOrNull()?.classId == if (sample.exercise == "SQUAT") 0 else 1),
                            "preprocessMs" to (preprocessingEnd - started) / 1e6,
                            "inferenceMs" to (nativeEnded - nativeStarted) / 1e6,
                            "timingScope" to "OFFLINE_STILL_IMAGE_TEST_NOT_CAMERA_PERFORMANCE")
                    } finally { bitmap.recycle() }
                }
            }
            val positiveClasses = reference.samples.flatMap { it.detections }.map { it.classId }.toSet()
            passed = rows.size >= 4 && rows.all { it["passed"] == true } && positiveClasses == setOf(0, 1)
            assertTrue("EXP03 actual Golden FAIL: preserve private results; do not widen thresholds/tolerances", passed)
        } catch (error: Throwable) {
            failure = when (error) {
                is MissingYoloModel -> "MODEL_MISSING"
                is YoloModelIdentityMismatch -> "MODEL_IDENTITY_MISMATCH"
                is UnsupportedYoloRuntime -> "UNSUPPORTED_RUNTIME"
                else -> "VALIDATION_FAILED"
            }
            throw error
        } finally {
            // Exclusive scalar-only evidence; no source pixels, raw tensor, account data or DB writes.
            require(resultFile.createNewFile())
            resultFile.setReadable(false, false); resultFile.setWritable(false, false)
            require(resultFile.setReadable(true, true) && resultFile.setWritable(true, true))
            resultFile.writeText(gson.toJson(mapOf("schemaVersion" to 1, "modelId" to identity.modelId,
                "modelSha256" to identity.sha256, "referenceSha256" to expectedReferenceHash,
                "nativeInferenceCount" to count, "actualImageCount" to rows.size,
                "allSamplesPassed" to passed, "failure" to failure, "tensorMetadata" to metadata,
                "productionContract" to Exp03YoloContract.verification.name, "contractPromotionPerformed" to false,
                "cameraPerformance" to "NOT_MEASURED", "samples" to rows)), Charsets.UTF_8)
        }
    }

    private fun validateReference(value: Reference) {
        require(value.schemaVersion == 1 && value.validationPurpose == "EXP03_ACTUAL_IMAGE_GOLDEN")
        require(value.model == Model(identity.modelId, identity.fileName, identity.bytes, identity.sha256))
        require(value.ultralyticsVersion == "8.4.202" && value.wheelSha256 ==
            "ae71dc5bb1024010fbdef5bd9281984ddd8c70885bddcfdd6aff0801b7015fb4")
        require(value.sourceSha256 == mapOf(
            "ultralytics/data/augment.py" to "414e49b76e5babfa118b88de60558e939fc436945cdf7a7ec4c09b0320a7036e",
            "ultralytics/engine/predictor.py" to "be124f7054a64139698434fba742f2d0a46dc73e32215ee0c750e6d429e423ec",
            "ultralytics/nn/backends/litert.py" to "e22fcde664c4e849ae26556a72cf77e890fdaf82031b1b239ce4571b4bace39f",
            "ultralytics/utils/ops.py" to "7b15cd980abbd578a8be91661c2399b2cbdea0207f1e61b0fb74cfac659bed77",
            "ultralytics/utils/nms.py" to "386cd0e3e38fddef5d434fbcfd58264bedfa37295534e9a2b7174a3436d4db62"))
        require(value.modelInferenceExecuted && !value.exportContractVerified && value.nativeInferenceCount == value.samples.size)
        require(value.thresholds == Thresholds(.10f, .70f, 300) && value.tolerances == Tolerances(.001f, 2f, 0, "EXACT"))
        require(value.samples.size in 4..64 && value.samples.map { it.id }.distinct().size == value.samples.size)
        require(value.samples.map { it.exercise to it.cameraView }.toSet() == setOf(
            "SQUAT" to "FRONT", "SQUAT" to "SIDE", "SHOULDER_PRESS" to "FRONT", "SHOULDER_PRESS" to "SIDE"))
        for (sample in value.samples) {
            require(sample.id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
            require(sample.rightsCleared && sample.rightsBasis.isNotBlank() && sample.modelInferenceExecuted)
            require(sample.sourceImageSha256.matches(Regex("[a-f0-9]{64}")))
            require(sample.rgbFile == "${sample.id}.rgb" && sample.inputTensorFile == "${sample.id}.input.f32" &&
                sample.nativeOutputFile == "${sample.id}.output.f32")
            require(sample.inputTensorByteOrder == "LITTLE_ENDIAN" && sample.nativeOutputByteOrder == "LITTLE_ENDIAN")
            Exp02FrameTransform.create(sample.width, sample.height) // bounds before raster allocation
            val detections = sample.detections.map { YoloGoldenDetection(it.classId, it.confidence,
                DetectionBox(it.bbox.x1, it.bbox.y1, it.bbox.x2, it.bbox.y2)) }
            require(YoloActualGoldenComparator.compare(detections, detections).passed)
        }
    }

    private fun requireGeometry(sample: Sample, transform: Exp02FrameTransform) = with(sample.preprocessing) {
        require(logicalColor == "RGB" && layout == "BCHW" && dtype == "FLOAT32" && normalizationDivisor == 255)
        require(paddingValue == 114 && centered && !auto)
        require(resizedWidth == transform.resizedWidth && resizedHeight == transform.resizedHeight &&
            padLeft == transform.padX && padTop == transform.padY &&
            padRight == 640 - resizedWidth - padLeft && padBottom == 640 - resizedHeight - padTop)
        require(kotlin.math.abs(gainX - transform.scaleX) < 1e-6 && kotlin.math.abs(gainY - transform.scaleY) < 1e-6)
    }

    private fun readFixture(directory: File, name: String, bytes: Long, expectedHash: String): ByteArray {
        require(expectedHash.matches(Regex("[a-f0-9]{64}")))
        val file = File(directory, name)
        require(file.canonicalFile == file.absoluteFile && file.canonicalFile.parentFile == directory && file.isFile && file.length() == bytes)
        return file.readBytes().also { require(hash(ByteBuffer.wrap(it)) == expectedHash) }
    }
    private fun invoke(runtime: YoloTensorRuntime, input: ByteBuffer): ByteBuffer {
        val output = ByteBuffer.allocateDirect(4 * 6 * 8400).order(ByteOrder.nativeOrder())
        input.rewind(); runtime.run(input, output); output.rewind()
        return output
    }
    private fun decode(raw: ByteBuffer, transform: YoloFrameTransform) = YoloRaw640Algorithms.postProcessor()
        .process(YoloRaw640Algorithms.decoder().decode(raw.duplicate().order(raw.order()), transform)).map {
            YoloGoldenDetection(YoloModelPolicy.classMapping.entries.single { entry -> entry.value == it.exerciseType }.key,
                requireNotNull(it.confidence), requireNotNull(it.boundingBox))
        }
    private fun maximumRawDifference(reference: ByteBuffer, actual: ByteBuffer): Double {
        val a = reference.duplicate().order(reference.order()); val b = actual.duplicate().order(actual.order())
        require(a.remaining() == b.remaining())
        var maximum = 0.0
        while (a.hasRemaining()) {
            val x = a.float; val y = b.float
            require(x.isFinite() && y.isFinite())
            maximum = maxOf(maximum, kotlin.math.abs(x.toDouble() - y))
        }
        return maximum
    }
    private fun hash(bytes: ByteBuffer) = MessageDigest.getInstance("SHA-256").apply { update(bytes.asReadOnlyBuffer()) }
        .digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}
