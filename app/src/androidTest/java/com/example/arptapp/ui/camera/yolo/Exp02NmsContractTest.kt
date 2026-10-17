package com.example.arptapp.ui.camera.yolo

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
 * Offline raw-output decoder/NMS evidence, isolated in the test APK. No images, models,
 * sessions, DB or network are accessed. Controlled evidence is never called actual-image
 * evidence; an actual case requires a provenance-bound frozen normalized model output.
 * Does NOT load an Interpreter, enable live prediction or alter contract verification.
 */
@RunWith(AndroidJUnit4::class)
class Exp02NmsContractTest {
    private val gson = Gson()
    private data class Thresholds(val confidence: Float, val iou: Float, val maxDetections: Int)
    private data class NormalizedOutput(val shape: List<Int>, val dtype: String, val byteOrder: String)
    private data class Comparison(val classMatch: String, val ordering: String,
        val confidenceTolerance: Float, val bboxTolerancePixels: Float)
    private data class Box(val x1: Float, val y1: Float, val x2: Float, val y2: Float)
    private data class Expected(val classId: Int, val confidence: Float, val bbox: Box)
    private data class Case(val id: String, val evidenceType: String,
        val rawOutputFile: String, val rawOutputSha256: String, val rawOutputByteOrder: String,
        val width: Int, val height: Int, val sourceImageSha256: String?,
        val sourceReferenceSha256: String?, val sourceSampleId: String?,
        val rawOutputProvenance: String?,
        val sourceModelInferenceExecuted: Boolean, val expected: List<Expected>,
        val inputAboveConfidenceCount: Int, val retainedAnchorIndicesBeforeClip: List<Int>,
        val nmsOutputCount: Int, val droppedFullyClippedCount: Int,
        val measuredPairIoU: Float?)
    private data class Reference(val schemaVersion: Int, val validationPurpose: String,
        val ultralyticsVersion: String, val wheelSha256: String, val modelSha256: String,
        val normalizedOutput: NormalizedOutput, val productionContract: String,
        val contractPromotionPerformed: Boolean, val predictionUsedForSession: Boolean,
        val modelInferenceExecutedByThisTool: Boolean, val thresholds: Thresholds,
        val comparison: Comparison, val cases: List<Case>)

    @Test fun sameRawOutputMatchesPinnedPythonNms() {
        assertTrue("Only a DEBUG target may expose local validation", BuildConfig.DEBUG)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val run = InstrumentationRegistry.getArguments().getString("nmsEvidenceRun") ?: error("Missing nmsEvidenceRun")
        require(run.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        val base = instrumentation.targetContext.noBackupFilesDir.canonicalFile
        val directory = File(base, "yolo/nms-contract/runs/$run")
        validateDirectory(base, directory)
        val target = File(directory, "android-nms-result.json")
        require(!target.exists()) { "NMS_EVIDENCE_RESULT_ALREADY_EXISTS_USE_NEW_RUN" }
        val results = mutableListOf<Map<String, Any?>>()
        var passed = false
        var failure = "NONE"
        try {
            val source = safeFile(directory, "reference.json", 1L..8_388_608L)
            val reference = gson.fromJson(source.readText(Charsets.UTF_8), Reference::class.java)
            validateReference(reference)
            for (case in reference.cases) {
                val payload = safeFile(directory, case.rawOutputFile, RAW_BYTES..RAW_BYTES).readBytes()
                require(hash(payload) == case.rawOutputSha256) { "RAW_SHA256_MISMATCH" }
                // File is explicitly LE. Convert values to native order, not a blind byte copy.
                val sourceFloats = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                val native = ByteBuffer.allocateDirect(payload.size).order(ByteOrder.nativeOrder())
                while (sourceFloats.hasRemaining()) native.putFloat(sourceFloats.float)
                native.rewind()
                val decoded = Exp02OutputDecoder().decode(native, Exp02FrameTransform.create(case.width, case.height))
                val actual = Exp02PostProcessor().process(decoded).map { detection ->
                    YoloGoldenDetection(YoloModelPolicy.classMapping.entries.single { it.value == detection.exerciseType }.key,
                        requireNotNull(detection.confidence), requireNotNull(detection.boundingBox))
                }
                val expected = case.expected.map { YoloGoldenDetection(it.classId, it.confidence,
                    DetectionBox(it.bbox.x1, it.bbox.y1, it.bbox.x2, it.bbox.y2)) }
                val comparison = YoloActualGoldenComparator.compare(expected, actual, YoloGoldenTolerance(1e-6f, 1e-4f))
                val above = decoded.candidates.filter { it.confidence > Exp02DetectionPolicy.APP_ENGINEERING_INITIAL_RAW_CONFIDENCE }
                require(above.size == case.inputAboveConfidenceCount) { "INPUT_CONFIDENCE_FILTER_MISMATCH" }
                val retained = case.retainedAnchorIndicesBeforeClip.toSet()
                val keptCandidates = above.filter { it.anchorIndex in retained }
                // Diagnostic witnesses use the PRODUCTION IoU helper, not a second NMS.
                // This does not decide the actual result; production process() already did.
                val witnesses = above.asSequence().filter { it.anchorIndex !in retained }.mapNotNull { rejected ->
                    keptCandidates.firstOrNull { kept -> kept.classId == rejected.classId &&
                        Exp02PostProcessor.iou(kept.modelBox, rejected.modelBox) > Exp02DetectionPolicy.IOU_THRESHOLD }?.let { kept ->
                        mapOf("keptAnchor" to kept.anchorIndex, "suppressedAnchor" to rejected.anchorIndex,
                            "classId" to kept.classId, "keptConfidence" to kept.confidence,
                            "suppressedConfidence" to rejected.confidence,
                            "modelBoxIou" to Exp02PostProcessor.iou(kept.modelBox, rejected.modelBox))
                    }
                }.take(16).toList()
                val boundaryPair = if (case.id in setOf("iou-below-threshold", "iou-above-threshold")) {
                    val pair = decoded.candidates.sortedBy { it.anchorIndex }
                    require(pair.size == 2)
                    Exp02PostProcessor.iou(pair[0].modelBox, pair[1].modelBox).also {
                        require(case.measuredPairIoU != null && kotlin.math.abs(it - case.measuredPairIoU) <= 1e-6f)
                        require((it > Exp02DetectionPolicy.IOU_THRESHOLD) == (case.id == "iou-above-threshold"))
                    }
                } else null
                results += mapOf("id" to case.id, "evidenceType" to case.evidenceType,
                    "passed" to comparison.passed, "rawOutputSha256" to case.rawOutputSha256,
                    "sourceImageSha256" to case.sourceImageSha256,
                    "sourceModelInferenceExecuted" to case.sourceModelInferenceExecuted,
                    "androidModelInferenceExecuted" to false, "inputAboveConfidenceCount" to above.size,
                    "referenceDetections" to expected, "detections" to actual,
                    "referenceClassOrdering" to expected.map { it.classId }, "classOrdering" to actual.map { it.classId },
                    "detectionsAfterNms" to actual.size, "comparison" to comparison,
                    "sameClassSuppressionWitnesses" to witnesses, "suppressionWitnessListLimitedTo" to 16,
                    "hasSameClassOverlapSuppressionWitness" to witnesses.isNotEmpty(),
                    "actualImageOverlapVerified" to (case.evidenceType == "ACTUAL_IMAGE_NMS" &&
                        above.size > case.nmsOutputCount && witnesses.isNotEmpty() && comparison.passed),
                    "actualImageOverlapStatus" to if (case.evidenceType != "ACTUAL_IMAGE_NMS") "NOT_APPLICABLE_CONTROLLED"
                        else if (above.size > case.nmsOutputCount && witnesses.isNotEmpty() && comparison.passed)
                            "PASS_ACTUAL_IMAGE_OVERLAP" else "NOT_OBSERVED_OR_COMPARISON_FAILED",
                    "measuredPairIoU" to boundaryPair)
            }
            passed = results.size == reference.cases.size && results.all { it["passed"] == true }
            assertTrue("Exact-source raw decoder/NMS comparison failed; do not tune thresholds/order/tolerance", passed)
        } catch (error: Throwable) {
            failure = "NMS_VALIDATION_FAILED"
            throw error
        } finally {
            validateDirectory(base, directory)
            val json = gson.toJson(mapOf("schemaVersion" to 1, "validationPurpose" to "EXP02_NMS_CONTRACT",
                "run" to run, "allCasesPassed" to passed, "failure" to failure,
                "modelSha256" to YoloModelPolicy.CANONICAL_SHA256,
                "androidModelInferenceExecuted" to false, "modelLoadPerformed" to false,
                "productionDecoderAndPostProcessorUsed" to true,
                "productionContract" to Exp02YoloContract.verification.name,
                "contractPromotionPerformed" to false, "predictionUsedForSession" to false,
                "thresholds" to mapOf("confidence" to .10f, "iou" to .70f, "maxDetections" to 300),
                "confidenceTolerance" to 1e-6f, "bboxTolerancePixels" to 1e-4f,
                "actualImageOverlapVerified" to results.any { it["actualImageOverlapVerified"] == true },
                "cases" to results))
            require(json.toByteArray(Charsets.UTF_8).size <= 8_388_608)
            // Atomic existence claim prevents a rerun/concurrent runner from overwriting evidence.
            require(target.createNewFile()) { "NMS_EVIDENCE_RESULT_ALREADY_EXISTS_USE_NEW_RUN" }
            Os.chmod(target.absolutePath, 384)
            target.writeText(json, Charsets.UTF_8)
        }
    }

    private fun validateReference(reference: Reference) {
        require(reference.schemaVersion == 1 && reference.validationPurpose == "EXP02_NMS_CONTRACT")
        require(reference.ultralyticsVersion == "8.4.176" && reference.wheelSha256 ==
            "15f4bbac5c69fd9a5a9395f5957a7ce561e3eb4620699a9c2ff11ed38ec7d34c")
        require(reference.modelSha256 == YoloModelPolicy.CANONICAL_SHA256)
        require(reference.normalizedOutput == NormalizedOutput(listOf(1, 6, 8400), "FLOAT32", "LITTLE_ENDIAN"))
        require(reference.productionContract in setOf("NOT_VERIFIED", "NOT_INSPECTED") && !reference.contractPromotionPerformed &&
            !reference.predictionUsedForSession && !reference.modelInferenceExecutedByThisTool)
        require(reference.thresholds == Thresholds(.10f, .70f, 300))
        require(reference.comparison == Comparison("EXACT", "EXACT_ORDER_EXCEPT_EXACT_SCORE_TIES_IN_BOTH_OUTPUTS", 1e-6f, 1e-4f))
        require(reference.cases.size in 6..14 && reference.cases.map { it.id }.distinct().size == reference.cases.size)
        val controlled = reference.cases.filter { it.evidenceType == "CONTROLLED_MODEL_FORMAT_NMS" }
        require(controlled.map { it.id }.toSet() == setOf("same-class-overlap", "iou-below-threshold",
            "iou-above-threshold", "different-class-overlap", "descending-order", "strict-confidence-boundary"))
        for (case in reference.cases) {
            require(case.id.matches(Regex("[a-z0-9][a-z0-9_-]{0,70}")))
            require(case.rawOutputFile == "${case.id}.output.f32" && case.rawOutputByteOrder == "LITTLE_ENDIAN")
            require(case.rawOutputSha256.matches(Regex("[a-f0-9]{64}")))
            Exp02FrameTransform.create(case.width, case.height)
            require(case.expected.size in 0..300 && case.nmsOutputCount in 0..300 && case.droppedFullyClippedCount in 0..300)
            require(case.nmsOutputCount == case.expected.size + case.droppedFullyClippedCount)
            require(case.inputAboveConfidenceCount in 0..8400)
            require(case.retainedAnchorIndicesBeforeClip.size == case.nmsOutputCount &&
                case.retainedAnchorIndicesBeforeClip.distinct().size == case.nmsOutputCount &&
                case.retainedAnchorIndicesBeforeClip.all { it in 0..8399 })
            when (case.evidenceType) {
                "CONTROLLED_MODEL_FORMAT_NMS" -> require(!case.sourceModelInferenceExecuted && case.sourceImageSha256 == null)
                "ACTUAL_IMAGE_NMS" -> require(case.sourceModelInferenceExecuted && case.id.startsWith("actual-") &&
                    case.rawOutputProvenance == "ACTUAL_NATIVE_NORMALIZED_GET_TENSOR_COPY_AFTER_REFERENCE_FORWARD" &&
                    case.sourceSampleId?.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")) == true &&
                    case.sourceImageSha256?.matches(Regex("[a-f0-9]{64}")) == true &&
                    case.sourceReferenceSha256?.matches(Regex("[a-f0-9]{64}")) == true)
                else -> error("Unknown evidence type")
            }
        }
    }

    private fun validateDirectory(base: File, directory: File) {
        require(directory.toPath().startsWith(base.toPath()))
        var current = directory
        while (true) {
            require(current.isDirectory && current.canonicalFile == current.absoluteFile &&
                OsConstants.S_ISDIR(Os.lstat(current.absolutePath).st_mode))
            if (current == base) break
            current = current.parentFile ?: error("Invalid private directory")
        }
    }

    private fun safeFile(directory: File, name: String, length: LongRange): File {
        require(name.matches(Regex("[a-z0-9][a-z0-9_.-]{0,95}")))
        val file = File(directory, name)
        require(file.isFile && file.length() in length && file.canonicalFile == file.absoluteFile &&
            file.canonicalFile.parentFile == directory.canonicalFile && OsConstants.S_ISREG(Os.lstat(file.absolutePath).st_mode))
        return file
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    companion object { private const val RAW_BYTES = 201_600L }
}
