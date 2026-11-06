package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.detection.*
import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in synthetic native/factory check only. No camera, Auth, Room or workout creation. */
@RunWith(AndroidJUnit4::class)
class YoloAbRuntimeDeviceTest {
    @Test fun compiledSandboxUsesItsOwnReviewedNativeModelAndCollector() {
        check(BuildConfig.DEBUG && BuildConfig.YOLO_AB_COMPARISON)
        val expected = requireNotNull(InstrumentationRegistry.getArguments().getString("expectedVariant"))
        require(expected == "EXP02" || expected == "EXP03")
        assertEquals(expected, BuildConfig.YOLO_MODEL_VARIANT)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val identity = if (expected == "EXP02") KnownYoloModels.EXP02 else KnownYoloModels.EXP03
        val expectedName = if (expected == "EXP02") "AIRPTCoach V1 - EXP02" else "AIRPTCoach V2 - EXP03"
        assertEquals("com.example.arptapp.${expected.lowercase()}", context.packageName)
        assertEquals(expectedName, context.applicationInfo.loadLabel(context.packageManager).toString())
        assertEquals(identity, YoloBuildProfile.identity)
        val contract = YoloBuildProfile.components()
        assertEquals(identity.modelId, contract.modelId)
        assertEquals(ExportContractVerification.VERIFIED, contract.verification)
        assertEquals(if (expected == "EXP02") Exp02BitmapPreprocessor::class.java else YoloBitmapPreprocessor::class.java,
            contract.preprocessor.javaClass)
        val queue = LinkedBlockingQueue<YoloShadowSnapshot>()
        val observer = YoloSessionDiagnosticsBridge(YoloSessionDiagnosticsCollector(identity), SystemClock::uptimeMillis)
        val now = SystemClock.uptimeMillis()
        observer.begin(System.currentTimeMillis(), now, CameraView.FRONT)
        val runner = requireNotNull(DebugYoloShadowFactory.create(context, { queue.offer(it) }, observer))
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff112233.toInt())
        try {
            runner.start()
            val initial = requireNotNull(queue.poll(10, TimeUnit.SECONDS))
            assertEquals(DetectorStatus.READY, initial.result.status)
            assertEquals(ExportContractVerification.VERIFIED, initial.result.contractVerification)
            assertTrue(initial.result.predictionAllowed)
            assertTrue(runner.trySubmit(bitmap, SystemClock.uptimeMillis()))
            val frame = requireNotNull(queue.poll(10, TimeUnit.SECONDS))
            assertEquals(DetectorStatus.READY, frame.result.status)
            assertNotNull(frame.result.timings)
            val stats = requireNotNull(observer.finish(System.currentTimeMillis(), SystemClock.uptimeMillis(), ExerciseType.SQUAT, 0))
            assertEquals(1L, stats.processedFrames)
            assertEquals(identity.modelId, stats.modelId)
            assertEquals(identity.sha256, stats.modelSha256)
            val root = context.noBackupFilesDir.canonicalFile
            val directory = File(root, "yolo/ab-runtime/${UUID.randomUUID()}")
            check(directory.canonicalPath == directory.absolutePath && directory.path.startsWith(root.path + File.separator))
            check(directory.mkdirs())
            val file = File(directory, "native-profile.json")
            check(file.createNewFile())
            file.setReadable(false, false); file.setWritable(false, false)
            check(file.setReadable(true, true) && file.setWritable(true, true))
            file.writeText(JSONObject().put("applicationId", context.packageName).put("appName", expectedName)
                .put("modelId", stats.modelId).put("modelSha256", stats.modelSha256).put("bytes", identity.bytes)
                .put("contractStatus", stats.contractStatus).put("modelStatus", stats.modelStatus)
                .put("predictionEnabled", stats.predictionEnabled).put("primary", "LANDMARK")
                .put("preprocessor", contract.preprocessor.javaClass.simpleName)
                .put("nativeInferenceCount", stats.processedFrames).put("fixture", "SYNTHETIC_SOLID_COLOR_NOT_CAMERA_NOT_GOLDEN")
                .put("cameraPerformance", "NOT_MEASURED").put("userDatabaseReadOrWritten", false)
                .toString(), Charsets.UTF_8)
        } finally { runner.close(); bitmap.recycle() }
    }
}
