package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import android.os.Build
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
 * Explicit instrumented validation ONLY. Not linked into the APK camera gate or production code.
 * Direct component invocation is essential to validate candidate code BEFORE promoting its gate.
 * No runtime/property/preferences bypass is created. Missing prerequisites FAIL, never pass/skip.
 */
@RunWith(AndroidJUnit4::class)
class Exp02ActualModelGoldenTest {
    private val gson = Gson()
    private data class Model(val id: String, val filename: String, val bytes: Long, val sha256: String)
    private data class Thresholds(val confidence: Float, val iou: Float, val maxDetections: Int)
    private data class Tolerances(val confidence: Float, val bboxPixels: Float, val detectionCount: Int, val classMatch: String)
    private data class Box(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    private data class Expected(val classId: Int, val confidence: Float, val bbox: Box)
    private data class Sample(
        val id: String, val exercise: String, val cameraView: String,
        val rgbFile: String, val rgbSha256: String, val width: Int, val height: Int,
        val inputTensorSha256: String, val detections: List<Expected>
    )
    private data class Reference(
        val schemaVersion: Int, val model: Model, val ultralyticsVersion: String, val wheelSha256: String,
        val modelInferenceExecuted: Boolean, val exportContractVerified: Boolean,
        val thresholds: Thresholds, val tolerances: Tolerances, val samples: List<Sample>
    )

    @Test fun actualFp32ModelMatchesRightsClearedPythonReference() {
        assertTrue("Actual-model harness is DEBUG-only", BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "yolo/actual-golden")
        val resultFile = File(directory, "android-result.json")
        val results = mutableListOf<Map<String, Any>>()
        var metadata: YoloTensorMetadata? = null
        var nativeInferenceCount = 0
        var passed = false
        var failure = "NONE"
        try {
            val file = File(directory, "reference.json")
            require(file.isFile && file.length() in 1..2_097_152) { "Provision the actual-model reference first" }
            val reference = gson.fromJson(file.readText(Charsets.UTF_8), Reference::class.java)
            validateReference(reference)
            // DebugYoloModelSource is private noBackup-only. TfliteYoloRuntime verifies SHA/size
            // before Interpreter construction. Runtime compatibility must actually succeed here.
            TfliteYoloRuntime(DebugYoloModelSource.open(context)).use { runtime ->
                metadata = runtime.metadata()
                assertTrue("Canonical FLOAT32 tensor metadata mismatch", metadata!!.matchesExpected())
                val preprocessor = Exp02BitmapPreprocessor()
                for (sample in reference.samples) {
                    val bitmap = decodeIdenticalRgb(directory, sample)
                    try {
                        val start = SystemClock.elapsedRealtimeNanos()
                        val prepared = preprocessor.preprocess(bitmap)
                        val preEnd = SystemClock.elapsedRealtimeNanos()
                        val tensorHash = sha256(prepared.tensor)
                        val output = ByteBuffer.allocateDirect(4 * 6 * 8400).order(ByteOrder.nativeOrder())
                        prepared.tensor.rewind()
                        val inferStart = SystemClock.elapsedRealtimeNanos()
                        runtime.run(prepared.tensor, output)
                        nativeInferenceCount++
                        val inferEnd = SystemClock.elapsedRealtimeNanos()
                        output.rewind()
                        val actual = Exp02PostProcessor().process(Exp02OutputDecoder().decode(output, prepared.transform))
                            .map { detection -> YoloGoldenDetection(
                                YoloModelPolicy.classMapping.entries.single { it.value == detection.exerciseType }.key,
                                requireNotNull(detection.confidence), requireNotNull(detection.boundingBox)) }
                        val postEnd = SystemClock.elapsedRealtimeNanos()
                        val expected = sample.detections.map { YoloGoldenDetection(it.classId, it.confidence,
                            DetectionBox(it.bbox.x1, it.bbox.y1, it.bbox.x2, it.bbox.y2)) }
                        val comparison = YoloActualGoldenComparator.compare(expected, actual)
                        results += mapOf("id" to sample.id, "exercise" to sample.exercise, "cameraView" to sample.cameraView,
                            "passed" to comparison.passed, "comparison" to comparison, "detections" to actual,
                            "inputTensorSha256" to tensorHash,
                            "inputTensorByteIdenticalToPython" to (tensorHash == sample.inputTensorSha256),
                            "preprocessMs" to (preEnd - start) / 1e6, "inferenceMs" to (inferEnd - inferStart) / 1e6,
                            "postprocessMs" to (postEnd - inferEnd) / 1e6, "totalMs" to (postEnd - start) / 1e6)
                    } finally { bitmap.recycle() }
                }
            }
            passed = results.size >= 4 && results.all { it["passed"] == true }
            assertTrue("Actual-model final detections differ; inspect private android-result.json, do not widen tolerances automatically", passed)
        } catch (error: Throwable) {
            failure = when (error) {
                is MissingYoloModel -> "MODEL_MISSING"
                is YoloModelIdentityMismatch -> "MODEL_IDENTITY_MISMATCH"
                is UnsupportedYoloRuntime -> "UNSUPPORTED_RUNTIME"
                else -> "VALIDATION_FAILED"
            }
            throw error
        } finally {
            // Only local validation results; no raster/landmarks/account identifiers, no upload.
            directory.mkdirs()
            resultFile.writeText(gson.toJson(mapOf(
                "schemaVersion" to 1, "androidNativeInferenceExecuted" to (nativeInferenceCount > 0),
                "nativeInferenceCount" to nativeInferenceCount,
                "allSamplesPassed" to passed, "failure" to failure, "sdkInt" to Build.VERSION.SDK_INT,
                "modelId" to YoloModelPolicy.MODEL_ID, "modelSha256" to YoloModelPolicy.CANONICAL_SHA256,
                "tensorMetadata" to metadata, "productionContract" to Exp02YoloContract.verification.name,
                "toleranceStatus" to "PROVISIONAL_NOT_MEASURED", "samples" to results)), Charsets.UTF_8)
        }
    }

    private fun validateReference(reference: Reference) {
        require(reference.schemaVersion == 1 && reference.modelInferenceExecuted && !reference.exportContractVerified)
        require(reference.model.id == YoloModelPolicy.MODEL_ID && reference.model.filename == YoloModelPolicy.FILE_NAME &&
            reference.model.bytes == YoloModelPolicy.CANONICAL_MODEL_BYTES && reference.model.sha256 == YoloModelPolicy.CANONICAL_SHA256)
        require(reference.ultralyticsVersion == "8.4.176" && reference.wheelSha256 ==
            "15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c")
        require(reference.thresholds == Thresholds(.10f, .70f, 300)) // No tuning in the validation step.
        require(reference.tolerances == Tolerances(.001f, 2f, 0, "EXACT"))
        require(reference.samples.size in 4..64)
        require(reference.samples.map { it.id }.distinct().size == reference.samples.size)
        val required = setOf("SQUAT" to "FRONT", "SQUAT" to "SIDE", "SHOULDER_PRESS" to "FRONT", "SHOULDER_PRESS" to "SIDE")
        require(reference.samples.map { it.exercise to it.cameraView }.toSet() == required)
        for (sample in reference.samples) {
            require(sample.id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")) && sample.rgbFile == "${sample.id}.rgb")
            require(sample.rgbSha256.matches(Regex("[a-f0-9]{64}")) && sample.inputTensorSha256.matches(Regex("[a-f0-9]{64}")))
            val expectedId = if (sample.exercise == "SQUAT") 0 else 1
            require(sample.detections.size in 1..300 && sample.detections.any { it.classId == expectedId })
            require(YoloActualGoldenComparator.compare(sample.detections.map { YoloGoldenDetection(it.classId, it.confidence,
                DetectionBox(it.bbox.x1, it.bbox.y1, it.bbox.x2, it.bbox.y2)) }, sample.detections.map { YoloGoldenDetection(it.classId, it.confidence,
                DetectionBox(it.bbox.x1, it.bbox.y1, it.bbox.x2, it.bbox.y2)) }).passed)
        }
    }

    private fun decodeIdenticalRgb(directory: File, sample: Sample): Bitmap {
        Exp02FrameTransform.create(sample.width, sample.height) // bounds before allocation/read
        val file = File(directory, sample.rgbFile)
        require(file.canonicalFile.parentFile == directory.canonicalFile && file.isFile &&
            file.length() == sample.width.toLong() * sample.height * 3)
        val bytes = file.readBytes()
        require(sha256(ByteBuffer.wrap(bytes)) == sample.rgbSha256)
        val pixels = IntArray(sample.width * sample.height) { index ->
            val base = index * 3
            (0xff shl 24) or ((bytes[base].toInt() and 255) shl 16) or
                ((bytes[base + 1].toInt() and 255) shl 8) or (bytes[base + 2].toInt() and 255)
        }
        return Bitmap.createBitmap(pixels, sample.width, sample.height, Bitmap.Config.ARGB_8888)
    }

    private fun sha256(buffer: ByteBuffer): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(buffer.asReadOnlyBuffer())
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
