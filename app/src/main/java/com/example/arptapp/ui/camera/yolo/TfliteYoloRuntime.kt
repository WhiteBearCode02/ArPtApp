package com.example.arptapp.ui.camera.yolo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.*
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** Reuses the existing TF Lite dependency; no delegate/SDK/download/network service is added. */
internal class TfliteYoloRuntime(private val model: ByteBuffer,
    private val identity: YoloModelDescriptor = YoloModelPolicy.identity) : YoloTensorRuntime {
    override val modelId get() = identity.modelId
    private val interpreter = try {
        YoloModelIdentity.validate(model, identity)
        Interpreter(model, Interpreter.Options().setNumThreads(2))
    } catch (error: Exception) {
        if (YoloRuntimeCompatibility.isUnsupported(error)) throw UnsupportedYoloRuntime()
        throw error
    }
    private var metadataLogged = false
    override fun metadata(): YoloTensorMetadata {
        val observed = YoloTensorMetadata(
            (0 until interpreter.inputTensorCount).map { index -> interpreter.getInputTensor(index).let { TensorSpec(it.shape().toList(), it.dataType().name) } },
            (0 until interpreter.outputTensorCount).map { index -> interpreter.getOutputTensor(index).let { TensorSpec(it.shape().toList(), it.dataType().name) } }
        )
        // Only validated bytes + directly observed native tensors earn a "loaded" log. No frame/account data.
        if (BuildConfig.DEBUG && !metadataLogged && observed.matchesExpected()) {
            metadataLogged = true
            Log.d("YoloModel", "AIRPTCoach model loaded · modelId=${identity.modelId} · file=${identity.fileName} · " +
                "input=${observed.inputs.single()} · output=${observed.outputs.single()} · classes=${YoloModelPolicy.classMapping} · primary=LANDMARK")
        }
        return observed
    }
    override fun run(input: ByteBuffer, output: ByteBuffer) {
        try { interpreter.run(input, output) } catch (error: Exception) {
            if (YoloRuntimeCompatibility.isUnsupported(error)) throw UnsupportedYoloRuntime()
            throw error
        }
    }
    override fun close() { interpreter.close() }
}

/** Private APK is explicit DEBUG-only opt-in; normal builds retain private-file-only loading. */
internal object DebugYoloModelSource {
    fun open(context: Context, identity: YoloModelDescriptor = YoloModelPolicy.identity): ByteBuffer {
        check(BuildConfig.DEBUG)
        if (!KnownYoloModels.contains(identity)) throw YoloModelIdentityMismatch()
        if (BuildConfig.YOLO_PRIVATE_BUNDLED) {
            check(!BuildConfig.YOLO_AB_COMPARISON && identity == KnownYoloModels.EXP02)
            // Fixed path, no network or public storage; validate exact bytes before native loading.
            return context.assets.openFd("yolo_private/exp02.bin").use { asset ->
                if (asset.declaredLength != identity.bytes) throw YoloModelIdentityMismatch()
                FileInputStream(asset.fileDescriptor).use { input ->
                    input.channel.map(FileChannel.MapMode.READ_ONLY, asset.startOffset, asset.declaredLength)
                        .also { YoloModelIdentity.validate(it, identity) }
                }
            }
        }
        val privateModel = File(context.noBackupFilesDir, "yolo/${identity.fileName}")
        if (!privateModel.isFile) throw MissingYoloModel()
        // No asset/network fallback. Reject path traversal/symlink escapes before touching bytes.
        val expectedDirectory = File(context.noBackupFilesDir, "yolo").canonicalFile
        if (expectedDirectory.parentFile != context.noBackupFilesDir.canonicalFile ||
            privateModel.canonicalFile.parentFile != expectedDirectory ||
            privateModel.canonicalFile.name != identity.fileName ||
            privateModel.length() != identity.bytes) throw YoloModelIdentityMismatch()
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
        val identity = YoloBuildProfile.identity
        val contract = YoloBuildProfile.components()
        return YoloShadowRunner(YoloExerciseDetector({ TfliteYoloRuntime(DebugYoloModelSource.open(app, identity), identity) }, contract,
            runtimePolicy = runtimePolicy),
            YoloExerciseStabilizer(contract.stabilization),
            copyFrame = { requireNotNull(it.copy(Bitmap.Config.ARGB_8888, false)) },
            releaseFrame = { it.recycle() }, onSnapshot = onSnapshot, observer = observer)
    }
}
