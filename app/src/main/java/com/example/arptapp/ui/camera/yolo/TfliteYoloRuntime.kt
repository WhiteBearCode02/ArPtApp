package com.example.arptapp.ui.camera.yolo

import android.content.Context
import android.graphics.Bitmap
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Reuses the existing TF Lite dependency; no delegate/SDK/download/network service is added. */
internal class TfliteYoloRuntime(private val model: ByteBuffer) : YoloTensorRuntime {
    private val interpreter = try {
        YoloModelIdentity.validate(model)
        Interpreter(model, Interpreter.Options().setNumThreads(2))
    } catch (error: Exception) {
        if (YoloRuntimeCompatibility.isUnsupported(error)) throw UnsupportedYoloRuntime()
        throw error
    }
    override fun metadata() = YoloTensorMetadata(
        (0 until interpreter.inputTensorCount).map { index -> interpreter.getInputTensor(index).let { TensorSpec(it.shape().toList(), it.dataType().name) } },
        (0 until interpreter.outputTensorCount).map { index -> interpreter.getOutputTensor(index).let { TensorSpec(it.shape().toList(), it.dataType().name) } }
    )
    override fun run(input: ByteBuffer, output: ByteBuffer) {
        try { interpreter.run(input, output) } catch (error: Exception) {
            if (YoloRuntimeCompatibility.isUnsupported(error)) throw UnsupportedYoloRuntime()
            throw error
        }
    }
    override fun close() { interpreter.close() }
}

/** Never stages/copies a model or weakens APK packaging. Both existing APK variants exclude weights. */
internal object DebugYoloModelSource {
    fun open(context: Context): ByteBuffer {
        check(BuildConfig.DEBUG)
        val privateModel = File(context.noBackupFilesDir, "yolo/${YoloModelPolicy.FILE_NAME}")
        if (!privateModel.isFile) throw MissingYoloModel()
        // No asset/network fallback. Reject path traversal/symlink escapes before touching bytes.
        val expectedDirectory = File(context.noBackupFilesDir, "yolo").canonicalFile
        if (expectedDirectory.parentFile != context.noBackupFilesDir.canonicalFile ||
            privateModel.canonicalFile.parentFile != expectedDirectory ||
            privateModel.canonicalFile.name != YoloModelPolicy.FILE_NAME ||
            privateModel.length() != YoloModelPolicy.CANONICAL_MODEL_BYTES) throw YoloModelIdentityMismatch()
        return FileInputStream(privateModel).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
    }
}

object DebugYoloShadowFactory {
    fun create(context: Context, onSnapshot: (YoloShadowSnapshot) -> Unit,
               observer: YoloShadowObserver = NoYoloShadowObserver): YoloShadowRunner<Bitmap>? {
        if (!BuildConfig.DEBUG) return null
        val app = context.applicationContext
        // Independent, explicit DEBUG rollout gate. Model presence and VERIFIED alone do not run frames.
        val runtimePolicy = YoloRuntimePolicy.forBuild(BuildConfig.DEBUG, BuildConfig.YOLO_SHADOW_ENABLED)
        val contract = Exp02YoloContract.components(Exp02BitmapPreprocessor())
        return YoloShadowRunner(YoloExerciseDetector({ TfliteYoloRuntime(DebugYoloModelSource.open(app)) }, contract,
            runtimePolicy = runtimePolicy),
            YoloExerciseStabilizer(contract.stabilization),
            copyFrame = { requireNotNull(it.copy(Bitmap.Config.ARGB_8888, false)) },
            releaseFrame = { it.recycle() }, onSnapshot = onSnapshot, observer = observer)
    }
}
