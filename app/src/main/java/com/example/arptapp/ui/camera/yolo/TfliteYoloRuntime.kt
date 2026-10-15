package com.example.arptapp.ui.camera.yolo

import android.content.Context
import android.graphics.Bitmap
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Reuses the existing TF Lite dependency; no delegate/SDK/download/network service is added. */
internal class TfliteYoloRuntime(private val model: ByteBuffer) : YoloTensorRuntime {
    private val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
    override fun metadata() = YoloTensorMetadata(
        (0 until interpreter.inputTensorCount).map { index -> interpreter.getInputTensor(index).let { TensorSpec(it.shape().toList(), it.dataType().name) } },
        (0 until interpreter.outputTensorCount).map { index -> interpreter.getOutputTensor(index).let { TensorSpec(it.shape().toList(), it.dataType().name) } }
    )
    override fun run(input: ByteBuffer, output: ByteBuffer) { interpreter.run(input, output) }
    override fun close() { interpreter.close() }
}

/** Never stages/copies a model or weakens APK packaging. Both existing APK variants exclude weights. */
internal object DebugYoloModelSource {
    fun open(context: Context): ByteBuffer {
        check(BuildConfig.DEBUG)
        val privateModel = File(context.noBackupFilesDir, "yolo/${YoloModelPolicy.FILE_NAME}")
        if (privateModel.isFile) {
            require(privateModel.length() in 1..YoloModelPolicy.MAX_MODEL_BYTES)
            return FileInputStream(privateModel).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
        }
        try {
            return context.assets.openFd(YoloModelPolicy.FILE_NAME).use { asset ->
                require(asset.declaredLength in 1..YoloModelPolicy.MAX_MODEL_BYTES)
                FileInputStream(asset.fileDescriptor).use {
                    it.channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
                }
            }
        } catch (_: FileNotFoundException) { throw MissingYoloModel() }
    }
}

object DebugYoloShadowFactory {
    fun create(context: Context, onSnapshot: (YoloShadowSnapshot) -> Unit): YoloShadowRunner<Bitmap>? {
        if (!BuildConfig.DEBUG) return null
        val app = context.applicationContext
        // TODO(EXP02_EXPORT_CONTRACT): replace the three ports ONLY with verified original-pipeline evidence.
        val contract = UnverifiedYoloContract.components<Bitmap>()
        return YoloShadowRunner(YoloExerciseDetector({ TfliteYoloRuntime(DebugYoloModelSource.open(app)) }, contract),
            YoloExerciseStabilizer(contract.stabilization),
            copyFrame = { requireNotNull(it.copy(Bitmap.Config.ARGB_8888, false)) },
            releaseFrame = { it.recycle() }, onSnapshot = onSnapshot)
    }
}
