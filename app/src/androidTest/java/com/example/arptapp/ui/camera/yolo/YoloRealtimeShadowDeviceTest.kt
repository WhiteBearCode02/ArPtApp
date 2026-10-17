package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import android.os.Process
import android.os.SystemClock
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import com.example.arptapp.data.AppDatabase
import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.detection.*
import com.example.arptapp.presentation.report.WorkoutReportData
import com.example.arptapp.presentation.report.YoloSessionDiagnosticsPresentation
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Actual native runner using a previously permissioned private Golden raster, NOT a camera session.
 * No new pictures, downloads or named user-DB rows. Scalars are exclusively written to a new
 * private noBackup run; Room fixtures are in memory only. Live-camera baseline is a separate step.
 */
@RunWith(AndroidJUnit4::class)
class YoloRealtimeShadowDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun verifiedDefaultOffLoadsModelWithoutPreprocessingOrNativeRun() {
        require(BuildConfig.DEBUG)
        var runs = 0
        val native = TfliteYoloRuntime(DebugYoloModelSource.open(context))
        val instrumented = object : YoloTensorRuntime {
            override fun metadata() = native.metadata()
            override fun run(input: ByteBuffer, output: ByteBuffer) { runs++; native.run(input, output) }
            override fun close() = native.close()
        }
        val contract = Exp02YoloContract.components<Bitmap>(YoloPreprocessor { error("OFF gate preprocessed a frame") })
        YoloExerciseDetector({ instrumented }, contract).use { detector ->
            val bitmap = loadExistingPrivateGoldenRaster()
            try {
                val result = detector.detect(bitmap)
                assertEquals(ExportContractVerification.VERIFIED, result.contractVerification)
                assertEquals(DetectorStatus.READY, result.status)
                assertFalse(result.shadowRuntimeEnabled); assertFalse(result.predictionAllowed)
                assertEquals(0, runs)
            } finally { bitmap.recycle() }
        }
    }

    @Test fun unverifiedContractCannotRunEvenWithDebugGateOn() {
        require(BuildConfig.DEBUG)
        var runs = 0
        val native = TfliteYoloRuntime(DebugYoloModelSource.open(context))
        val instrumented = object : YoloTensorRuntime {
            override fun metadata() = native.metadata()
            override fun run(input: ByteBuffer, output: ByteBuffer) { runs++; native.run(input, output) }
            override fun close() = native.close()
        }
        YoloExerciseDetector({ instrumented }, UnverifiedYoloContract.components<Int>(),
            runtimePolicy = YoloRuntimePolicy.forBuild(true, true)).use { detector ->
            val result = detector.detect(0)
            assertEquals(DetectorStatus.CONTRACT_NOT_VERIFIED, result.status)
            assertTrue(result.shadowRuntimeEnabled); assertFalse(result.predictionAllowed)
            assertEquals(0, runs)
        }
    }

    @Test fun enabledFactoryRunsNativeAndRoundTripsOnlyAggregates() = runBlocking {
        require(BuildConfig.DEBUG && BuildConfig.YOLO_SHADOW_ENABLED)
        val target = evidenceFile("controlled-shadow.json")
        require(!target.exists()) { "Keep previous evidence; use a new shadowEvidenceRun" }
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val bridge = YoloSessionDiagnosticsBridge(monotonicMs = SystemClock::uptimeMillis)
        val bitmap = loadExistingPrivateGoldenRaster()
        val runner = requireNotNull(DebugYoloShadowFactory.create(context, { queue.offer(it) }, bridge))
        try {
            runner.start()
            val initialized = requireNotNull(queue.poll(10, TimeUnit.SECONDS))
            assertEquals(DetectorStatus.READY, initialized.result.status)
            assertTrue(initialized.result.predictionAllowed)
            bridge.begin(System.currentTimeMillis(), SystemClock.uptimeMillis(), CameraView.UNKNOWN)
            // This is a known SQUAT fixture repeated for runner coverage, not a physical exercise.
            bridge.updateContext(ExerciseType.SQUAT, -1)
            repeat(4) {
                // A result callback precedes the worker's finally/release; exactly 250ms does not
                // guarantee ownership is released on a real scheduler. Simulate dropped camera
                // submissions until admitted, bounded by the unchanged inference timeout.
                val admissionDeadline = SystemClock.uptimeMillis() + YoloShadowPolicy.INFERENCE_TIMEOUT_MS
                var submittedAt: Long
                while (true) {
                    submittedAt = SystemClock.uptimeMillis()
                    if (runner.trySubmit(bitmap, submittedAt)) break
                    assertTrue("No admission before deadline; queued state=${queue.peek()?.result?.status}",
                        submittedAt < admissionDeadline)
                    SystemClock.sleep(10)
                }
                val completed = requireNotNull(queue.poll(10, TimeUnit.SECONDS))
                assertEquals(DetectorStatus.READY, completed.result.status)
                assertNotNull(completed.result.timings)
                assertEquals(ExerciseType.SQUAT, completed.result.exerciseType)
                // Keep the existing 250ms admission interval, never tune it to make this test pass.
                SystemClock.sleep((YoloShadowPolicy.MIN_FRAME_INTERVAL_MS - (SystemClock.uptimeMillis() - submittedAt)).coerceAtLeast(1))
            }
            val aggregate = requireNotNull(bridge.finish(System.currentTimeMillis(), SystemClock.uptimeMillis(), ExerciseType.SQUAT, -1))
            assertEquals(4L, aggregate.processedFrames)
            assertEquals(4L, aggregate.rawDetectionCounts["SQUAT"])
            assertEquals(true, aggregate.shadowRuntimeEnabled); assertTrue(aggregate.predictionEnabled)
            assertNotNull(aggregate.averageProcessedFps); assertNotNull(aggregate.averageInferenceMs)
            val encoded = requireNotNull(YoloSessionDiagnosticsCodec.encode(aggregate))
            val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
            try {
                val report = WorkoutReportData.create(exerciseType = "SQUAT", totalCount = 2, averageScore = 85f,
                    analysesJson = "[80,90]", durationSeconds = 12, yoloDiagnosticsJson = encoded).completedAt("2000-01-01 00:00")
                database.exerciseDao().insertRecord(report.toRecord("in-memory-shadow-fixture"))
                val loaded = database.exerciseDao().getAllRecords("in-memory-shadow-fixture").single()
                assertEquals(report, WorkoutReportData.fromRecord(loaded))
                val rows = YoloSessionDiagnosticsPresentation.groups(requireNotNull(YoloSessionDiagnosticsCodec.decode(loaded.yoloDiagnosticsJson)))
                    .flatMap { it.rows }.associate { it.label to it.value }
                assertEquals("VERIFIED", rows["Export 계약"]); assertEquals("ENABLED", rows["Shadow Runtime"])
                assertTrue(rows["Primary"]!!.startsWith("LANDMARK"))
                assertEquals(2, loaded.totalCount); assertEquals(85f, loaded.averageScore, 0f)
                assertEquals("[80,90]", loaded.scoresJson)
            } finally { database.close() }
            writeExclusive(target, JSONObject().put("schemaVersion", 1)
                .put("validationPurpose", "CONTROLLED_REPEAT_PRIVATE_GOLDEN_NOT_PHYSICAL_SESSION")
                .put("processId", Process.myPid()).put("primary", "LANDMARK")
                .put("namedUserDatabaseModified", false).put("cameraFramesSaved", false)
                .put("diagnostics", JSONObject(encoded)))
        } finally { runner.close(); bitmap.recycle() }
    }

    @Test fun readControlledAggregateAfterRestart() {
        val source = JSONObject(evidenceFile("controlled-shadow.json").readText(Charsets.UTF_8))
        require(source.getInt("processId") != Process.myPid()) { "Force-stop before this separate readback" }
        assertEquals("CONTROLLED_REPEAT_PRIVATE_GOLDEN_NOT_PHYSICAL_SESSION", source.getString("validationPurpose"))
        val value = requireNotNull(YoloSessionDiagnosticsCodec.decode(source.getJSONObject("diagnostics").toString()))
        assertEquals("VERIFIED", value.contractStatus); assertEquals(true, value.shadowRuntimeEnabled)
        assertEquals(4L, value.processedFrames)
        writeExclusive(evidenceFile("controlled-readback.json"), JSONObject().put("schemaVersion", 1)
            .put("sourceProcessId", source.getInt("processId")).put("processId", Process.myPid())
            .put("controlledAggregateReadbackPassed", true).put("notUserDatabaseReadback", true))
    }

    private fun loadExistingPrivateGoldenRaster(): Bitmap {
        // Fixed existing private fixture, no arbitrary Intent/path/URI or image generation.
        val directory = File(context.noBackupFilesDir.canonicalFile, "yolo/positive-golden")
        require(directory.canonicalPath == directory.absolutePath)
        val referenceFile = File(directory, "reference.json")
        require(referenceFile.canonicalFile.parentFile == directory && referenceFile.length() in 1..2_097_152)
        val reference = JSONObject(referenceFile.readText(Charsets.UTF_8))
        assertEquals(YoloModelPolicy.CANONICAL_SHA256, reference.getJSONObject("model").getString("sha256"))
        val samples = reference.getJSONArray("samples")
        val sample = (0 until samples.length()).map { samples.getJSONObject(it) }.single { it.getString("id") == "candidate_02" }
        val width = sample.getInt("width"); val height = sample.getInt("height")
        require(width in 1..4096 && height in 1..4096 && width.toLong() * height <= 4_194_304)
        val filename = sample.getString("rgbFile")
        require(filename.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}\\.rgb")))
        val file = File(directory, filename)
        require(file.canonicalFile.parentFile == directory && file.length() == width.toLong() * height * 3)
        val rgb = file.readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(rgb).joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == sample.getString("rgbSha256"))
        val pixels = IntArray(width * height) { index ->
            val offset = index * 3
            (0xff shl 24) or ((rgb[offset].toInt() and 255) shl 16) or
                ((rgb[offset + 1].toInt() and 255) shl 8) or (rgb[offset + 2].toInt() and 255)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun evidenceFile(name: String): File {
        require(BuildConfig.DEBUG)
        val run = requireNotNull(InstrumentationRegistry.getArguments().getString("shadowEvidenceRun"))
        require(run.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")))
        val directory = File(context.noBackupFilesDir.canonicalFile, "yolo/realtime-shadow/$run")
        require(directory.canonicalPath == directory.absolutePath)
        require(directory.isDirectory || directory.mkdirs())
        return File(directory, name).also { require(it.canonicalFile.parentFile == directory) }
    }

    private fun writeExclusive(file: File, result: JSONObject) {
        require(file.createNewFile()) { "Keep previous evidence; no overwrite" }
        file.setReadable(false, false); file.setWritable(false, false)
        require(file.setReadable(true, true) && file.setWritable(true, true))
        file.writeText(result.toString(), Charsets.UTF_8)
    }
}
