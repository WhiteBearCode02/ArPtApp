package com.example.arptapp.ui.camera.yolo

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*
import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in final app smoke: no Auth/camera/Room/real workout. Needs an already provisioned private EXP02. */
@RunWith(AndroidJUnit4::class)
class ProductionExp02RuntimeDeviceTest {
    @Test fun normalAppLoadsOnlyExp02WithItsReviewedContractAndRunsOneNativeFrame() {
        check(BuildConfig.DEBUG && !BuildConfig.YOLO_AB_COMPARISON)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.example.arptapp", context.packageName)
        assertEquals("EXP02", BuildConfig.YOLO_MODEL_VARIANT)
        val identity = YoloBuildProfile.identity
        assertEquals(KnownYoloModels.EXP02, identity)
        val contract = YoloBuildProfile.components()
        assertEquals(identity.modelId, contract.modelId)
        assertEquals(Exp02BitmapPreprocessor::class.java, contract.preprocessor.javaClass)
        val observed = TfliteYoloRuntime(DebugYoloModelSource.open(context, identity), identity).use { native ->
            assertEquals(identity.modelId, native.modelId)
            native.metadata().also { assertTrue(it.matchesExpected()) }
        }
        val snapshots = LinkedBlockingQueue<YoloShadowSnapshot>()
        val runner = requireNotNull(DebugYoloShadowFactory.create(context, { snapshots.offer(it) }))
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xff112233.toInt())
        try {
            runner.start()
            val initial = requireNotNull(snapshots.poll(10, TimeUnit.SECONDS))
            assertEquals(DetectorStatus.READY, initial.result.status)
            assertEquals(ExportContractVerification.VERIFIED, initial.result.contractVerification)
            assertTrue(initial.result.predictionAllowed)
            assertTrue(runner.trySubmit(bitmap, SystemClock.uptimeMillis()))
            val completed = requireNotNull(snapshots.poll(10, TimeUnit.SECONDS))
            assertEquals(DetectorStatus.READY, completed.result.status)
            assertTrue(completed.result.predictionAllowed)
            assertNotNull(completed.result.timings)
            val root = context.noBackupFilesDir.canonicalFile
            val directory = File(root, "yolo/production-runtime/${UUID.randomUUID()}")
            check(directory.canonicalPath == directory.absolutePath && directory.path.startsWith(root.path + File.separator))
            check(directory.mkdirs())
            val file = File(directory, "native-profile.json")
            check(file.createNewFile())
            file.setReadable(false, false); file.setWritable(false, false)
            check(file.setReadable(true, true) && file.setWritable(true, true))
            file.writeText(JSONObject().put("applicationId", context.packageName).put("selectedVersion", "EXP02")
                .put("modelId", identity.modelId).put("modelFile", identity.fileName).put("modelSha256", identity.sha256)
                .put("bytes", identity.bytes).put("input", observed.inputs.single().toString())
                .put("output", observed.outputs.single().toString()).put("preprocessor", contract.preprocessor.javaClass.simpleName)
                .put("modelStatus", completed.result.status.name).put("contractStatus", completed.result.contractVerification.name)
                .put("predictionEnabled", completed.result.predictionAllowed).put("primary", "LANDMARK")
                .put("nativeInferenceCount", 1).put("fixture", "SYNTHETIC_SOLID_COLOR_NOT_CAMERA_NOT_GOLDEN")
                .put("cameraPerformance", "NOT_MEASURED").put("userDatabaseReadOrWritten", false).toString(), Charsets.UTF_8)
        } finally { runner.close(); bitmap.recycle() }
    }
}
