package com.example.arptapp.ui.camera.yolo.validation

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import com.example.arptapp.BuildConfig
import com.example.arptapp.domain.detection.Exp02YoloContract
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit offline validation only; NOT a YoloShadowRunner/ExerciseDetector gate bypass. */
object DebugPositiveValidationFactory {
    fun create(context: Context): Exp02PositiveValidation? =
        if (BuildConfig.DEBUG) DebugPositiveValidation(context.applicationContext) else null
}

private class DebugPositiveValidation(context: Context) : Exp02PositiveValidation {
    private val store = DebugPositiveCandidateStore(context.noBackupFilesDir)
    private val inference = DebugPositiveNativeInference(context)
    private val worker = Executors.newSingleThreadExecutor { task -> Thread(task, "exp02-explicit-validation") }
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    override fun saveAndAnalyze(frame: Bitmap, callback: (PositiveCandidateAnalysis) -> Unit): Boolean {
        if (closed.get() || !busy.compareAndSet(false, true)) return false
        val copy = try {
            check(!frame.isRecycled)
            DebugPositiveCandidateStore.requireDimensions(frame.width, frame.height)
            // Copy only after the explicit call is admitted. No retained live/cached camera frame.
            requireNotNull(frame.copy(Bitmap.Config.ARGB_8888, false)).apply { setHasAlpha(false) }
        } catch (_: Exception) {
            busy.set(false)
            main.post { if (!closed.get()) callback(failure(null, "INVALID_FRAME")) }
            return true
        } catch (_: LinkageError) {
            // Missing/incompatible native symbols are recoverable validation failures, not a
            // reason to terminate the app. Do not catch arbitrary VM errors (e.g. OOM).
            busy.set(false)
            main.post { if (!closed.get()) callback(failure(null, "UNSUPPORTED_RUNTIME")) }
            return true
        }
        try {
            worker.execute {
                var candidate: PositiveCandidate? = null
                val result = try {
                    inference.resetDiagnostics()
                    val savedCandidate = store.save(copy)
                    candidate = savedCandidate
                    store.decode(savedCandidate).let { saved ->
                        try { inference.analyze(savedCandidate, saved) } finally { saved.recycle() }
                    }.also { store.writeResult(savedCandidate, inference.json(savedCandidate, it)) }
                } catch (error: Exception) {
                    recordFailure(candidate, DebugPositiveNativeInference.failureCode(error))
                } catch (error: LinkageError) {
                    // TensorFlow/LiteRT JNI may throw UnsatisfiedLinkError/NoClassDefFoundError
                    // outside Exception. Isolate only linkage failures; fatal VM errors escape.
                    recordFailure(candidate, DebugPositiveNativeInference.failureCode(error))
                } finally {
                    try { copy.recycle() } finally { busy.set(false) }
                }
                main.post { if (!closed.get()) callback(result) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            copy.recycle()
            busy.set(false)
            return false
        }
        return true
    }

    override fun list(callback: (PositiveCandidateInventory) -> Unit) = inventoryOperation(callback) {
        PositiveCandidateInventory(store.inventory(), "READY")
    }

    override fun deleteAll(callback: (PositiveCandidateInventory) -> Unit) = inventoryOperation(callback) {
        val deleted = store.deleteOwned()
        PositiveCandidateInventory(store.inventory(), "DELETED", deleted)
    }

    private fun inventoryOperation(callback: (PositiveCandidateInventory) -> Unit, operation: () -> PositiveCandidateInventory): Boolean {
        if (closed.get() || !busy.compareAndSet(false, true)) return false
        try {
            worker.execute {
                val result = try { operation() }
                catch (_: Exception) { PositiveCandidateInventory(emptyList(), "PRIVATE_STORAGE_REJECTED") }
                catch (_: LinkageError) { PositiveCandidateInventory(emptyList(), "UNSUPPORTED_RUNTIME") }
                finally { busy.set(false) }
                main.post { if (!closed.get()) callback(result) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) { busy.set(false); return false }
        return true
    }

    override fun close() {
        closed.set(true)
        // Let the one admitted operation finish safely; never strand a retained bitmap or half file.
        worker.shutdown()
    }

    private fun recordFailure(candidate: PositiveCandidate?, code: String): PositiveCandidateAnalysis {
        val result = failure(candidate, code).copy(nativeInferenceExecuted = inference.nativeInferenceExecuted)
        // Preserve a successfully saved input and truthful native-execution status even if decoder,
        // runtime close, or JSON persistence failed. Never synthesize a detection to fill the gap.
        if (candidate != null) {
            try { store.writeResult(candidate, inference.json(candidate, result)) }
            catch (_: Exception) { /* Best effort: the callback still reports the real failure. */ }
            catch (_: LinkageError) { /* No native/JSON dependency failure may escape this boundary. */ }
        }
        return result
    }

    private fun failure(candidate: PositiveCandidate?, code: String) = PositiveCandidateAnalysis(candidate, code,
        nativeInferenceExecuted = false, detections = emptyList(),
        message = "검증 전용 · ${Exp02YoloContract.verification.name} 유지 · $code")
}
