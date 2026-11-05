package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Synthetic native smoke is NOT actual-image Golden equivalence and never promotes the contract. */
@RunWith(AndroidJUnit4::class)
class Exp03NativeContractDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun canonicalExp03LoadsReportsFloat32TensorsAndRunsNativeSmoke() {
        check(BuildConfig.DEBUG)
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff112233.toInt()) // in-memory synthetic fixture only, never saved/uploaded
        try {
            TfliteYoloRuntime(DebugYoloModelSource.open(context)).use { runtime ->
                assertEquals(KnownYoloModels.EXP03.modelId, runtime.modelId)
                val metadata = runtime.metadata()
                assertTrue(metadata.matchesExpected())
                val prepared = YoloBitmapPreprocessor().preprocess(bitmap)
                assertEquals(17f / 255, prepared.tensor.getFloat(0), 1e-6f)
                assertEquals(34f / 255, prepared.tensor.getFloat(640 * 640 * 4), 1e-6f)
                assertEquals(51f / 255, prepared.tensor.getFloat(640 * 640 * 8), 1e-6f)
                val output = ByteBuffer.allocateDirect(4 * 6 * 8400).order(ByteOrder.nativeOrder())
                val started = SystemClock.elapsedRealtimeNanos()
                runtime.run(prepared.tensor, output)
                val nativeMs = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
                output.rewind()
                val floats = output.asFloatBuffer()
                while (floats.hasRemaining()) assertTrue(floats.get().isFinite())
                val detections = YoloRaw640Algorithms.postProcessor().process(
                    YoloRaw640Algorithms.decoder().decode(output, prepared.transform))
                val root = context.noBackupFilesDir.canonicalFile
                val directory = File(root, "yolo/exp03-native/runs/${UUID.randomUUID()}")
                require(directory.canonicalFile == directory.absoluteFile && directory.canonicalPath.startsWith(root.path + File.separator))
                require(directory.mkdirs())
                val file = File(directory, "native-smoke.json")
                require(file.createNewFile())
                file.setReadable(false, false); file.setWritable(false, false)
                require(file.setReadable(true, true) && file.setWritable(true, true))
                file.writeText(JSONObject().put("modelId", runtime.modelId).put("sha256", YoloModelPolicy.CANONICAL_SHA256)
                    .put("bytes", YoloModelPolicy.CANONICAL_MODEL_BYTES).put("input", "FLOAT32 [1,3,640,640]")
                    .put("output", "FLOAT32 [1,6,8400]").put("nativeInferenceCount", 1).put("nativeMs", nativeMs)
                    .put("fixture", "SYNTHETIC_SOLID_COLOR_NOT_CAMERA_NOT_GOLDEN").put("detectionsAfterNms", detections.size)
                    .put("contractStatus", Exp03YoloContract.verification.name).put("contractPromotionPerformed", false)
                    .put("cameraPerformance", "NOT_MEASURED").toString(), Charsets.UTF_8)
                assertEquals(ExportContractVerification.NOT_VERIFIED, Exp03YoloContract.verification)
            }
        } finally { bitmap.recycle() }
    }

    @Test fun currentFactoryStaysClosedWithoutExp03GoldenEvidence() {
        check(BuildConfig.DEBUG)
        val snapshots = LinkedBlockingQueue<YoloShadowSnapshot>()
        val runner = requireNotNull(DebugYoloShadowFactory.create(context, { snapshots.offer(it) }))
        try {
            runner.start()
            val snapshot = requireNotNull(snapshots.poll(10, TimeUnit.SECONDS))
            assertEquals(DetectorStatus.CONTRACT_NOT_VERIFIED, snapshot.result.status)
            assertEquals(ExportContractVerification.NOT_VERIFIED, snapshot.result.contractVerification)
            assertFalse(snapshot.result.predictionAllowed)
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            try { assertFalse(runner.trySubmit(bitmap, SystemClock.uptimeMillis())) }
            finally { bitmap.recycle() }
        } finally { runner.close() }
    }
}
