package com.example.arptapp.domain.detection

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import java.util.concurrent.atomic.AtomicReference

/** Immutable admission context: a frame submitted before the primary locks stays unclassified. */
data class YoloShadowObservationContext(val token: Long, val expectedExercise: ExerciseType, val thermalStatus: Int)

/** Scalar-only observer port. No Activity/View/frame reference crosses the worker boundary. */
interface YoloShadowObserver {
    fun captureContext(): YoloShadowObservationContext? = null
    fun onFrameSeen(context: YoloShadowObservationContext) = Unit
    fun onAdmission(context: YoloShadowObservationContext) = Unit
    fun onResult(context: YoloShadowObservationContext?, snapshot: YoloShadowSnapshot, isFrame: Boolean) = Unit
}

object NoYoloShadowObserver : YoloShadowObserver

/**
 * Connects camera admission to a thread-safe collector, never to the workout classifier.
 * Main-thread lifecycle/context updates are tiny scalar writes; statistics stay on the worker.
 * A late frame retains its originating token and cannot leak into a newly started session.
 */
class YoloSessionDiagnosticsBridge(
    private val collector: YoloSessionDiagnosticsCollector = YoloSessionDiagnosticsCollector(),
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000 }
) : YoloShadowObserver {
    private val active = AtomicReference<YoloShadowObservationContext?>(null)
    @Volatile private var latest = YoloShadowSnapshot(ExerciseDetectionResult(DetectorStatus.INITIALIZING, DetectionSource.YOLO))

    @Synchronized fun begin(startedAtEpochMs: Long, startedAtMonotonicMs: Long, cameraView: CameraView) {
        val token = collector.reset(startedAtEpochMs, startedAtMonotonicMs, cameraView, latest)
        active.set(YoloShadowObservationContext(token, ExerciseType.UNKNOWN, -1))
    }

    @Synchronized fun updateContext(expectedExercise: ExerciseType, thermalStatus: Int) {
        active.get()?.let { active.set(it.copy(expectedExercise = expectedExercise, thermalStatus = thermalStatus)) }
    }

    @Synchronized fun finish(endedAtEpochMs: Long, endedAtMonotonicMs: Long,
                             expectedExercise: ExerciseType, thermalStatus: Int): YoloSessionDiagnostics? {
        val context = active.getAndSet(null) ?: return null
        return collector.finish(context.token, endedAtEpochMs, endedAtMonotonicMs, expectedExercise, thermalStatus)
    }

    fun current(observedAtEpochMs: Long, observedAtMonotonicMs: Long): YoloSessionDiagnostics? {
        val context = active.get() ?: return null
        return collector.snapshot(context.token, observedAtEpochMs, observedAtMonotonicMs,
            context.expectedExercise, context.thermalStatus)
    }

    override fun captureContext(): YoloShadowObservationContext? = active.get()
    override fun onFrameSeen(context: YoloShadowObservationContext) = collector.observeFrameSeen(context.token)
    override fun onAdmission(context: YoloShadowObservationContext) = collector.observeAdmission(context.token)
    @Synchronized override fun onResult(context: YoloShadowObservationContext?, snapshot: YoloShadowSnapshot, isFrame: Boolean) {
        val current = active.get()
        val staleFrame = isFrame && current != null && context?.token != current.token
        // Stale predictions are discarded. A native timeout/failure disables the shared runner,
        // however, so propagate that GLOBAL availability change without counting an old frame.
        if (staleFrame && snapshot.result.status in listOf(DetectorStatus.READY, DetectorStatus.INITIALIZING)) return
        latest = snapshot
        val countFrame = isFrame && !staleFrame
        val origin = if (countFrame) context else current
        origin ?: return
        collector.observeSnapshot(origin.token, snapshot, origin.expectedExercise, origin.thermalStatus, monotonicMs(), countFrame)
    }
}
