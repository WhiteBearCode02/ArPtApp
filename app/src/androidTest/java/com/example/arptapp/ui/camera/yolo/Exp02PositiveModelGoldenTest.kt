package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*
import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Positive-only evidence gate in the instrumented test APK, never in a workout/session pipeline.
 * Directly exercises the same production components WITHOUT changing its NOT_VERIFIED constant.
 * Requires an actual private Python reference; no synthetic fallback or auto-verification exists.
 */
@RunWith(AndroidJUnit4::class)
class Exp02PositiveModelGoldenTest {
    private val gson = Gson()
    private data class Model(val id: String, val filename: String, val bytes: Long, val sha256: String)
    private data class Thresholds(val confidence: Float, val iou: Float, val maxDetections: Int)
    private data class Tolerances(val confidence: Float, val bboxPixels: Float, val detectionCount: Int, val classMatch: String)
    private data class Box(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    private data class Expected(val classId: Int, val confidence: Float, val bbox: Box)
    private data class Sample(
        val id: String, val exercise: String?, val cameraView: String?,
        val rgbFile: String, val rgbSha256: String, val width: Int, val height: Int,
        val sourceImageSha256: String?,
        val inputTensorFile: String, val inputTensorSha256: String, val inputTensorByteOrder: String,
        val detections: List<Expected>
    )
    private data class Reference(
        val schemaVersion: Int, val validationPurpose: String, val model: Model,
        val ultralyticsVersion: String, val wheelSha256: String,
        val modelInferenceExecuted: Boolean, val exportContractVerified: Boolean,
        val thresholds: Thresholds, val tolerances: Tolerances, val samples: List<Sample>
    )

    @Test fun actualCandidateMatchesPythonReference() {
        assertTrue("Harness must never execute against a release target", BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val arguments = InstrumentationRegistry.getArguments()
        val runId = arguments.getString("positiveEvidenceRun")
        require(runId == null || runId.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        val base = File(context.noBackupFilesDir.canonicalFile, "yolo/positive-golden")
        val directory = if (runId == null) base else File(base, "runs/$runId")
        validateDirectory(directory)
        val target = File(directory, "android-positive-result.json")
        require(!target.exists()) { "Previous evidence exists; choose a new run instead of overwriting" }
        val requiredClass = arguments.getString("requiredPositiveClass")
        require(requiredClass == null || requiredClass in setOf("SQUAT", "SHOULDER_PRESS"))
        val requiredClassId = requiredClass?.let { label -> YoloModelPolicy.classMapping.entries.single { it.value.name == label }.key }
        val results = mutableListOf<Map<String, Any?>>()
        var nativeCount = 0
        var positivePairs = 0
        var requiredClassPairs = 0
        var passed = false
        var failure = "NONE"
        var metadata: YoloTensorMetadata? = null
        try {
            val referenceFile = safeFile(directory, "reference.json", 1L..2_097_152L)
            val reference = gson.fromJson(referenceFile.readText(Charsets.UTF_8), Reference::class.java)
            validateReference(reference)
            TfliteYoloRuntime(DebugYoloModelSource.open(context)).use { runtime ->
                metadata = runtime.metadata()
                require(metadata!!.matchesExpected()) { "TENSOR_MISMATCH" }
                val preprocessor = Exp02BitmapPreprocessor()
                for (sample in reference.samples) {
                    val bitmap = decodeRgb(directory, sample)
                    try {
                        val prepared = preprocessor.preprocess(bitmap)
                        val referenceTensor = safeFile(directory, sample.inputTensorFile, 4_915_200L..4_915_200L).readBytes()
                        require(hash(referenceTensor) == sample.inputTensorSha256)
                        val preprocessing = YoloActualGoldenTensorComparator.compare(
                            ByteBuffer.wrap(referenceTensor).order(ByteOrder.LITTLE_ENDIAN), prepared.tensor,
                            prepared.transform as Exp02FrameTransform)
                        val output = ByteBuffer.allocateDirect(4 * 6 * 8400).order(ByteOrder.nativeOrder())
                        prepared.tensor.rewind()
                        val start = SystemClock.elapsedRealtimeNanos()
                        runtime.run(prepared.tensor, output)
                        nativeCount++
                        val inferenceMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
                        output.rewind()
                        val actual = Exp02PostProcessor().process(Exp02OutputDecoder().decode(output, prepared.transform)).map {
                            YoloGoldenDetection(YoloModelPolicy.classMapping.entries.single { entry -> entry.value == it.exerciseType }.key,
                                requireNotNull(it.confidence), requireNotNull(it.boundingBox))
                        }
                        val expected = sample.detections.map { YoloGoldenDetection(it.classId, it.confidence,
                            DetectionBox(it.bbox.x1, it.bbox.y1, it.bbox.x2, it.bbox.y2)) }
                        val comparison = YoloActualGoldenComparator.compare(expected, actual)
                        val positive = expected.isNotEmpty() && actual.isNotEmpty()
                        if (positive && preprocessing.passed && comparison.passed) positivePairs++
                        if (positive && preprocessing.passed && comparison.passed && requiredClassId != null &&
                            expected.any { it.classId == requiredClassId } && actual.any { it.classId == requiredClassId }) requiredClassPairs++
                        val expectedId = sample.exercise?.let { label -> YoloModelPolicy.classMapping.entries.single { it.value.name == label }.key }
                        results += mapOf("id" to sample.id, "exercise" to sample.exercise, "cameraView" to sample.cameraView,
                            "sourceImageSha256" to sample.sourceImageSha256, "rgbSha256" to sample.rgbSha256,
                            "passed" to (preprocessing.passed && comparison.passed),
                            "positiveEvidence" to positive, "preprocessingComparison" to preprocessing,
                            "comparison" to comparison, "detections" to actual, "referenceDetections" to expected,
                            "predictedClassNames" to actual.map { YoloModelPolicy.classMapping.getValue(it.classId).name },
                            "classOrdering" to actual.map { it.classId }, "referenceClassOrdering" to expected.map { it.classId },
                            "detectionsAfterNms" to actual.size, "referenceDetectionsAfterNms" to expected.size,
                            "semanticSanity" to mapOf("expectedExercise" to sample.exercise,
                                "evaluated" to (expectedId != null),
                                "topPredictionMatchesExpected" to expectedId?.let { actual.firstOrNull()?.classId == it },
                                "hasExpectedDetection" to expectedId?.let { id -> actual.any { it.classId == id } }),
                            "inferenceMs" to inferenceMs)
                    } finally { bitmap.recycle() }
                }
            }
            passed = results.isNotEmpty() && results.all { it["passed"] == true } && positivePairs > 0 &&
                (requiredClassId == null || requiredClassPairs > 0)
            if (positivePairs == 0) failure = "NO_POSITIVE_EQUIVALENCE_EVIDENCE"
            else if (requiredClassId != null && requiredClassPairs == 0) failure = "REQUIRED_CLASS_POSITIVE_NOT_OBSERVED"
            assertTrue("Positive Golden needs >=1 actual positive equivalent detection; do not lower thresholds/widen tolerances", passed)
        } catch (error: Throwable) {
            if (failure == "NONE") failure = when (error) {
                is MissingYoloModel -> "MODEL_MISSING"
                is YoloModelIdentityMismatch -> "MODEL_IDENTITY_MISMATCH"
                is UnsupportedYoloRuntime -> "UNSUPPORTED_RUNTIME"
                else -> "VALIDATION_FAILED"
            }
            throw error
        } finally {
            validateDirectory(directory)
            require(target.canonicalFile == target.absoluteFile && target.createNewFile())
            Os.chmod(target.absolutePath, 384)
            val json = gson.toJson(mapOf("schemaVersion" to 1, "validationPurpose" to "POSITIVE_CANDIDATE",
                "androidNativeInferenceExecuted" to (nativeCount > 0), "nativeInferenceCount" to nativeCount,
                "positiveEquivalentPairs" to positivePairs, "allSamplesPassed" to passed, "failure" to failure,
                "evidenceRun" to runId, "requiredPositiveClass" to requiredClass, "requiredClassPositivePairs" to requiredClassPairs,
                "modelId" to YoloModelPolicy.MODEL_ID, "modelSha256" to YoloModelPolicy.CANONICAL_SHA256,
                "tensorMetadata" to metadata, "productionContract" to Exp02YoloContract.verification.name,
                "contractPromotionPerformed" to false, "predictionUsedForSession" to false,
                "orderingPolicy" to "EXACT_ORDER_EXCEPT_EXACT_SCORE_TIES_IN_BOTH_OUTPUTS",
                "semanticEvaluationPolicy" to "EXPLICIT_USER_LABEL_ONLY_NOT_A_GATE_OR_OVERALL_ACCURACY", "samples" to results))
            require(json.toByteArray(Charsets.UTF_8).size <= 2_097_152)
            target.writeText(json, Charsets.UTF_8)
            Os.chmod(target.absolutePath, 384)
        }
    }

    private fun validateReference(reference: Reference) {
        require(reference.schemaVersion == 1 && reference.validationPurpose == "POSITIVE_CANDIDATE" &&
            reference.modelInferenceExecuted && !reference.exportContractVerified)
        require(reference.model == Model(YoloModelPolicy.MODEL_ID, YoloModelPolicy.FILE_NAME,
            YoloModelPolicy.CANONICAL_MODEL_BYTES, YoloModelPolicy.CANONICAL_SHA256))
        require(reference.ultralyticsVersion == "8.4.176" && reference.wheelSha256 ==
            "15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c")
        require(reference.thresholds == Thresholds(.10f, .70f, 300))
        require(reference.tolerances == Tolerances(.001f, 2f, 0, "EXACT"))
        require(reference.samples.size in 1..8 && reference.samples.map { it.id }.distinct().size == reference.samples.size)
        for (sample in reference.samples) {
            require(sample.id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
            require(sample.rgbFile == "${sample.id}.rgb" && sample.inputTensorFile == "${sample.id}.input.f32")
            require(sample.rgbSha256.matches(Regex("[a-f0-9]{64}")) && sample.inputTensorSha256.matches(Regex("[a-f0-9]{64}")))
            require(sample.sourceImageSha256 == null || sample.sourceImageSha256.matches(Regex("[a-f0-9]{64}")))
            require(sample.inputTensorByteOrder == "LITTLE_ENDIAN")
            require(sample.exercise == null || sample.exercise in setOf("SQUAT", "SHOULDER_PRESS"))
            require(sample.cameraView == null || sample.cameraView in setOf("FRONT", "SIDE"))
            Exp02FrameTransform.create(sample.width, sample.height)
            require(sample.detections.size in 0..300)
            val detections = sample.detections.map { YoloGoldenDetection(it.classId, it.confidence,
                DetectionBox(it.bbox.x1, it.bbox.y1, it.bbox.x2, it.bbox.y2)) }
            require(YoloActualGoldenComparator.compare(detections, detections).passed)
        }
    }

    private fun decodeRgb(directory: File, sample: Sample): Bitmap {
        val length = sample.width.toLong() * sample.height * 3
        val bytes = safeFile(directory, sample.rgbFile, length..length).readBytes()
        require(hash(bytes) == sample.rgbSha256)
        val pixels = IntArray(sample.width * sample.height) { index ->
            val offset = index * 3
            (0xff shl 24) or ((bytes[offset].toInt() and 255) shl 16) or
                ((bytes[offset + 1].toInt() and 255) shl 8) or (bytes[offset + 2].toInt() and 255)
        }
        return Bitmap.createBitmap(pixels, sample.width, sample.height, Bitmap.Config.ARGB_8888)
    }

    private fun validateDirectory(directory: File) {
        val parent = directory.parentFile!!
        require(parent.isDirectory && directory.isDirectory)
        require(parent.canonicalFile == parent.absoluteFile && directory.canonicalFile == directory.absoluteFile)
        require(OsConstants.S_ISDIR(Os.lstat(parent.absolutePath).st_mode) &&
            OsConstants.S_ISDIR(Os.lstat(directory.absolutePath).st_mode))
    }

    private fun safeFile(directory: File, name: String, bytes: LongRange): File {
        require(name.matches(Regex("[a-z0-9][a-z0-9_.-]{0,95}")))
        val file = File(directory, name)
        require(file.isFile && file.length() in bytes && file.canonicalFile == file.absoluteFile &&
            file.canonicalFile.parentFile == directory.canonicalFile && OsConstants.S_ISREG(Os.lstat(file.absolutePath).st_mode))
        return file
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
