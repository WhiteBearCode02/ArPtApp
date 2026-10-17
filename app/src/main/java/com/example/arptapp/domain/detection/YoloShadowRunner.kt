package com.example.arptapp.domain.detection

import com.example.arptapp.domain.classifier.ExerciseType
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** ENGINEERING_INITIAL scheduling only. Not a measured model FPS or accuracy policy. */
object YoloShadowPolicy {
    const val MIN_FRAME_INTERVAL_MS = 250L
    const val INITIALIZATION_TIMEOUT_MS = 5000L
    const val INFERENCE_TIMEOUT_MS = 2000L
}

/** One admitted task at a time, no frame backlog. Timestamp is monotonic and never logged. */
class ShadowFrameGate(private val intervalMs: Long = YoloShadowPolicy.MIN_FRAME_INTERVAL_MS) {
    init { require(intervalMs > 0) }
    private var busy = false
    private var disabled = false
    private var lastAccepted: Long? = null
    @Synchronized fun tryAcquire(timestampMs: Long): Boolean {
        if (disabled || busy || timestampMs < 0 || lastAccepted?.let { timestampMs - it < intervalMs } == true) return false
        busy = true; lastAccepted = timestampMs; return true
    }
    @Synchronized fun release() { busy = false }
    @Synchronized fun disable() { disabled = true }
}

data class YoloShadowSnapshot(
    val result: ExerciseDetectionResult,
    val stableExercise: ExerciseType = ExerciseType.UNKNOWN,
    /** Originating admission epoch, not the lens generation at completion/UI dispatch. */
    val generation: Long = 0
)

/**
 * Isolated native owner. Watchdog never closes an interpreter concurrently with a native invocation.
 * A timeout disables admissions immediately; late work is discarded and its resources closed on its owner.
 * Frame copies are made ONLY after READY + single-flight admission, never for an unavailable contract.
 */
class YoloShadowRunner<F>(
    private val detector: ExerciseDetector<F>,
    private val stabilizer: YoloExerciseStabilizer,
    private val copyFrame: (F) -> F,
    private val releaseFrame: (F) -> Unit,
    private val onSnapshot: (YoloShadowSnapshot) -> Unit,
    private val worker: ExecutorService = Executors.newSingleThreadExecutor(),
    private val timer: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(),
    private val initializationTimeoutMs: Long = YoloShadowPolicy.INITIALIZATION_TIMEOUT_MS,
    private val inferenceTimeoutMs: Long = YoloShadowPolicy.INFERENCE_TIMEOUT_MS,
    private val observer: YoloShadowObserver = NoYoloShadowObserver
) : AutoCloseable {
    private val started = AtomicBoolean()
    private val closed = AtomicBoolean()
    private val failed = AtomicBoolean()
    private val epoch = AtomicLong()
    private val gate = ShadowFrameGate()
    private val evidenceLock = Any()
    @Volatile private var latest = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.INITIALIZING, DetectionSource.YOLO))

    fun start() {
        if (closed.get() || !started.compareAndSet(false, true)) return
        launch(initializationTimeoutMs, null) { detector.initialize() }
    }

    /** Called with the same rotated, non-mirrored source used by MediaPipe; caller retains its ownership. */
    fun trySubmit(frame: F, timestampMs: Long): Boolean {
        val observation = try { observer.captureContext() } catch (_: Exception) { null }
        observation?.let { observeSafely { observer.onFrameSeen(it) } }
        if (closed.get() || failed.get() || !latest.result.predictionAllowed || !gate.tryAcquire(timestampMs)) return false
        observation?.let { observeSafely { observer.onAdmission(it) } }
        val generation = epoch.get()
        val owned = try { copyFrame(frame) } catch (_: Exception) {
            gate.release(); disable(DetectorStatus.INFERENCE_FAILED, observation); return false
        }
        if (closed.get() || failed.get()) { releaseFrame(owned); gate.release(); return false }
        launch(inferenceTimeoutMs, owned, generation, timestampMs, observation) { detector.detect(owned) }
        return true
    }

    private fun launch(timeoutMs: Long, frame: F?, generation: Long = epoch.get(), timestampMs: Long = 0,
                       observation: YoloShadowObservationContext? = null,
                       operation: () -> ExerciseDetectionResult) {
        val completed = AtomicBoolean()
        val deadline = try { timer.schedule({
            if (completed.compareAndSet(false, true) && !closed.get()) disable(DetectorStatus.TIMED_OUT, observation)
        }, timeoutMs, TimeUnit.MILLISECONDS) } catch (_: Exception) {
            frame?.let(releaseFrame); if (frame != null) gate.release(); return
        }
        try {
            worker.execute {
                try {
                    if (closed.get() || failed.get()) return@execute
                    val result = try { operation() } catch (_: Exception) {
                        latest.result.copy(status = DetectorStatus.INFERENCE_FAILED, selected = null, detectionCount = 0, timings = null)
                    } catch (_: LinkageError) {
                        latest.result.copy(status = DetectorStatus.INFERENCE_FAILED, selected = null, detectionCount = 0, timings = null)
                    }
                    if (completed.compareAndSet(false, true) && !closed.get() && !failed.get()) {
                        synchronized(evidenceLock) {
                            if ((frame == null || generation == epoch.get()) && !closed.get() && !failed.get()) {
                                val stable = if (result.predictionAllowed && frame != null)
                                    stabilizer.observe(result.selected, timestampMs) else ExerciseType.UNKNOWN
                                // Initialization is lens-independent; frame results retain their admission epoch.
                                latest = YoloShadowSnapshot(result, stable, if (frame == null) epoch.get() else generation)
                                if (result.status !in listOf(DetectorStatus.READY, DetectorStatus.INITIALIZING)) gate.disable()
                                observeSafely { observer.onResult(observation, latest, frame != null) }
                                onSnapshot(latest)
                            }
                        }
                    }
                } finally {
                    deadline.cancel(false)
                    frame?.let(releaseFrame)
                    // Initialization never acquired the frame gate. Releasing it here could admit a
                    // second frame if the READY callback already submitted the first frame.
                    if (frame != null) gate.release()
                    if (closed.get() || failed.get()) runCatching { detector.close() }
                }
            }
        } catch (_: Exception) {
            completed.set(true); deadline.cancel(false); frame?.let(releaseFrame); if (frame != null) gate.release()
        }
    }

    /** UI dispatch checks the originating epoch; availability/errors do not belong to any camera lens. */
    fun isCurrent(snapshot: YoloShadowSnapshot): Boolean = !closed.get() &&
        (snapshot.result.status != DetectorStatus.READY || snapshot.generation == epoch.get())

    /** Lens/view/session changes discard obsolete evidence, without touching the workout session. */
    fun invalidate() = synchronized(evidenceLock) {
        val nextGeneration = epoch.incrementAndGet()
        stabilizer.reset()
        if (!closed.get() && latest.result.status == DetectorStatus.READY) {
            latest = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.YOLO,
                contractVerification = latest.result.contractVerification,
                shadowRuntimeEnabled = latest.result.shadowRuntimeEnabled),
                generation = nextGeneration)
            observeSafely { observer.onResult(null, latest, false) }
            onSnapshot(latest)
        }
    }
    private fun disable(status: DetectorStatus, observation: YoloShadowObservationContext? = null) {
        failed.set(true); gate.disable()
        synchronized(evidenceLock) {
            stabilizer.reset()
            if (!closed.get()) {
                latest = YoloShadowSnapshot(ExerciseDetectionResult(status, DetectionSource.YOLO,
                    contractVerification = latest.result.contractVerification,
                    shadowRuntimeEnabled = latest.result.shadowRuntimeEnabled), generation = epoch.get())
                observeSafely { observer.onResult(observation, latest, observation != null) }
                onSnapshot(latest)
            }
        }
    }
    private inline fun observeSafely(action: () -> Unit) {
        // Diagnostics may fail independently; they must never break admission or native ownership.
        try { action() } catch (_: Exception) { /* No frame or native exception payload is logged. */ }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        gate.disable(); invalidate(); timer.shutdownNow()
        // shutdown(), not shutdownNow(): a queued owned frame must run its finally/release path.
        runCatching { worker.execute { runCatching { detector.close() } } }
        worker.shutdown()
    }
}
