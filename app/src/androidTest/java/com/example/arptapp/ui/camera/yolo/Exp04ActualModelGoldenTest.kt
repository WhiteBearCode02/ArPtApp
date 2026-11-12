package com.example.arptapp.ui.camera.yolo

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.domain.detection.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opt-in still-image comparison only. NEVER changes Exp04YoloContract or camera policy. */
@RunWith(AndroidJUnit4::class)
class Exp04ActualModelGoldenTest {
    private val profile = YoloModelProfiles.EXP04

    @Test fun preprocessingOnlyMatchesPythonBitForBit() {
        val fixtures = Exp04GoldenFixtures()
        val rows = fixtures.reference.samples.map { sample ->
            val bitmap = fixtures.bitmap(sample)
            try {
                val prepared = YoloBitmapPreprocessor().preprocess(bitmap)
                val transform = prepared.transform as Exp02FrameTransform
                fixtures.geometry(sample, transform)
                mapOf("id" to sample.id, "byteIdentical" to (Exp04GoldenFixtures.hash(prepared.tensor) == sample.payloads.getValue(".input.f32").sha256),
                    "comparison" to YoloActualGoldenTensorComparator.compare(fixtures.payload(sample, ".input.f32"), prepared.tensor, transform))
            } finally { bitmap.recycle() }
        }
        fixtures.writeNewResult("android-preprocessing.json", mapOf("modelId" to profile.descriptor.modelId,
            "referenceSha256" to fixtures.referenceHash, "modelInferenceExecuted" to false, "samples" to rows))
        assertTrue("Preprocessing mismatch: do not invoke native Golden or widen tolerance", rows.all { it["byteIdentical"] == true })
    }

    @Test fun actualExp04MatchesExactVersionPythonReference() {
        val fixtures = Exp04GoldenFixtures()
        require(!File(fixtures.directory, "android-result.json").exists())
        val identity = profile.descriptor
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rows = mutableListOf<Map<String, Any>>()
        var count = 0
        var passed = false
        var failure = "NONE"
        try {
            TfliteYoloRuntime(DebugYoloModelSource.open(context, identity), identity).use { runtime ->
                assertTrue(runtime.metadata().matchesExpected(profile))
                for (sample in fixtures.reference.samples) {
                    val bitmap = fixtures.bitmap(sample)
                    try {
                        val prepared = YoloBitmapPreprocessor().preprocess(bitmap)
                        val transform = prepared.transform as Exp02FrameTransform
                        fixtures.geometry(sample, transform)
                        val referenceInput = fixtures.payload(sample, ".input.f32")
                        val pixelComparison = YoloActualGoldenTensorComparator.compare(referenceInput, prepared.tensor, transform)
                        val identical = Exp04GoldenFixtures.hash(prepared.tensor) == sample.payloads.getValue(".input.f32").sha256
                        if (!identical) {
                            rows += mapOf("id" to sample.id, "passed" to false, "status" to "PREPROCESSING_NOT_BYTE_IDENTICAL",
                                "inputTensorByteIdenticalToPython" to false, "preprocessingComparison" to pixelComparison)
                            error("Exact preprocessing mismatch; native inference intentionally blocked")
                        }
                        val expected = sample.detections.map { it.golden() }
                        val frozenRaw = YoloActualGoldenComparator.compare(expected, decode(fixtures.payload(sample, ".output.f32"), transform),
                            YoloGoldenTolerance(1e-6f, 1e-4f), profile)
                        val started = SystemClock.elapsedRealtimeNanos()
                        val output = invoke(runtime, prepared.tensor)
                        count++
                        val actual = decode(output, transform)
                        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
                        val comparison = YoloActualGoldenComparator.compare(expected, actual, profile = profile)
                        val samplePassed = pixelComparison.passed && comparison.passed && frozenRaw.passed
                        rows += mapOf("id" to sample.id, "exercise" to sample.exercise, "cameraView" to sample.cameraView,
                            "passed" to samplePassed, "inputTensorByteIdenticalToPython" to identical,
                            "preprocessingComparison" to pixelComparison, "comparison" to comparison,
                            "frozenPythonRawDecodeNmsComparison" to frozenRaw,
                            "detections" to actual, "referenceDetections" to expected,
                            "classOrdering" to actual.map { it.classId }, "referenceClassOrdering" to sample.classOrdering,
                            "semanticMatch" to (actual.firstOrNull()?.classId == profile.classMapping.entries.single { it.value.name == sample.exercise }.key),
                            "inferenceAndPostprocessMs" to elapsedMs, "timingScope" to "OFFLINE_STILL_IMAGE_NOT_CAMERA_PERFORMANCE")
                    } finally { bitmap.recycle() }
                }
            }
            // Equivalence and semantic accuracy are separate: do not discard wrong/empty predictions.
            passed = rows.size == fixtures.reference.samples.size && rows.all { it["passed"] == true }
            assertTrue("EXP04 actual Golden FAIL; retain evidence and NOT_VERIFIED", passed)
        } catch (error: Throwable) {
            failure = when (error) {
                is MissingYoloModel -> "MODEL_MISSING"
                is YoloModelIdentityMismatch -> "MODEL_IDENTITY_MISMATCH"
                is UnsupportedYoloRuntime -> "UNSUPPORTED_RUNTIME"
                else -> "VALIDATION_FAILED"
            }
            throw error
        } finally {
            fixtures.writeNewResult("android-result.json", mapOf("schemaVersion" to 1, "modelId" to identity.modelId,
                "modelSha256" to identity.sha256, "referenceSha256" to fixtures.referenceHash, "nativeInferenceCount" to count,
                "actualImageCount" to rows.size, "allSamplesPassed" to passed, "failure" to failure,
                "expectedClassCoverage" to fixtures.reference.samples.map { it.exercise }.distinct(),
                "pythonPositiveClassCoverage" to fixtures.reference.samples.flatMap { it.detections }.map { it.classId }.distinct(),
                "productionContract" to Exp04YoloContract.verification.name, "contractPromotionPerformed" to false,
                "cameraPerformance" to "NOT_MEASURED", "samples" to rows))
        }
    }

    private fun invoke(runtime: YoloTensorRuntime, input: ByteBuffer): ByteBuffer {
        val output = ByteBuffer.allocateDirect(profile.outputBytes).order(ByteOrder.nativeOrder())
        input.rewind(); runtime.run(input, output); output.rewind()
        return output
    }
    private fun decode(raw: ByteBuffer, transform: YoloFrameTransform) = YoloRaw640Algorithms.postProcessor(profile)
        .process(YoloRaw640Algorithms.decoder(profile).decode(raw.duplicate().order(raw.order()), transform)).map {
            YoloGoldenDetection(profile.classMapping.entries.single { entry -> entry.value == it.exerciseType }.key,
                requireNotNull(it.confidence), requireNotNull(it.boundingBox))
        }
}
