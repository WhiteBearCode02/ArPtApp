package com.example.arptapp.domain.detection

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseType
import java.util.Collections
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * Local-only, bounded session aggregate. Never contains a frame, box, landmark, account or LLM DTO.
 * Enum names are persisted instead of localized display labels so historical diagnostics remain stable.
 */
@Serializable
data class YoloSessionDiagnostics(
    val schemaVersion: Int = 1,
    val modelId: String = YoloModelPolicy.MODEL_ID,
    val modelSha256: String = YoloModelPolicy.CANONICAL_SHA256,
    val modelStatus: String,
    val contractStatus: String,
    val predictionEnabled: Boolean,
    val totalFramesSeen: Long = 0,
    val admittedFrames: Long = 0,
    val processedFrames: Long = 0,
    val activeDurationMs: Long = 0,
    /** Successful native frame completions / active monotonic session seconds; NOT MediaPipe FPS. */
    val averageProcessedFps: Double? = null,
    val averagePreprocessMs: Double? = null,
    val averageInferenceMs: Double? = null,
    val averagePostprocessMs: Double? = null,
    val averageTotalMs: Double? = null,
    val confidenceSampleCount: Long = 0,
    val averageConfidence: Double? = null,
    val minConfidence: Double? = null,
    val maxConfidence: Double? = null,
    /** Android thermal status 0..6; null means no supported reading, not NORMAL. */
    val maxThermalStatus: Int? = null,
    val rawDetectionCounts: Map<String, Long> = emptyMap(),
    val stableDetectionCounts: Map<String, Long> = emptyMap(),
    val expectedSessionExercise: String = ExerciseType.UNKNOWN.name,
    val matchingStableFrames: Long = 0,
    val mismatchingStableFrames: Long = 0,
    val unknownStableFrames: Long = 0,
    /** Successful frames before the primary lock; never retroactively compared after a lock. */
    val unknownSessionFrames: Long = 0,
    val cameraView: String = CameraView.UNKNOWN.name,
    val startedAt: Long,
    val endedAt: Long,
    /** Independent gate, not model readiness. null preserves pre-gate historical records honestly. */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val shadowRuntimeEnabled: Boolean? = null
) {
    /** UNKNOWN is not a negative label and is excluded from this denominator. */
    val knownMatchRate: Double? get() = (matchingStableFrames + mismatchingStableFrames)
        .takeIf { it > 0 }?.let { matchingStableFrames.toDouble() / it }

    /** Only successful frames sampled after a supported primary lock enter this denominator. */
    val unknownStableRate: Double? get() = (matchingStableFrames + mismatchingStableFrames + unknownStableFrames)
        .takeIf { it > 0 }?.let { unknownStableFrames.toDouble() / it }
}

/**
 * Thread-safe, constant-space collector. Admission captures the session token; a callback must reuse
 * that token, never look up the current session on completion. finish() freezes and closes a token.
 * Initialization, invalidation and watchdog/status notifications must pass isFrame=false.
 */
class YoloSessionDiagnosticsCollector {
    private var nextToken = 0L
    private var active: State? = null

    @Synchronized
    fun reset(startedAtEpochMs: Long, startedAtMonotonicMs: Long, cameraView: CameraView,
              initialSnapshot: YoloShadowSnapshot): Long {
        require(startedAtEpochMs >= 0 && startedAtMonotonicMs >= 0)
        nextToken = if (nextToken == Long.MAX_VALUE) 1 else nextToken + 1
        active = State(nextToken, startedAtEpochMs, startedAtMonotonicMs, cameraView,
            initialSnapshot.result.status, initialSnapshot.result.contractVerification, initialSnapshot.result.shadowRuntimeEnabled)
        return nextToken
    }

    @Synchronized
    fun observeFrameSeen(token: Long) {
        state(token)?.let { it.seen = increment(it.seen) }
    }

    @Synchronized
    fun observeAdmission(token: Long) {
        state(token)?.let { if (it.admitted < it.seen) it.admitted = increment(it.admitted) }
    }

    @Synchronized
    fun observeSnapshot(token: Long, snapshot: YoloShadowSnapshot, expectedSessionExercise: ExerciseType,
                        thermalStatus: Int, completedAtMonotonicMs: Long, isFrame: Boolean = true) {
        val state = state(token) ?: return
        state.status = snapshot.result.status
        state.contract = snapshot.result.contractVerification
        state.shadowEnabled = snapshot.result.shadowRuntimeEnabled
        state.observeThermal(thermalStatus)
        val timings = snapshot.result.timings
        // READY initialization/invalidation is not an inference. Failed frames contribute no fake zeros.
        if (!isFrame || !snapshot.result.predictionAllowed || timings == null ||
            completedAtMonotonicMs < state.monotonicStart || state.processed >= state.admitted ||
            listOf(timings.preprocessMs, timings.inferenceMs, timings.postprocessMs, timings.totalMs).any { it < 0 }) return
        state.processed = increment(state.processed)
        state.preprocess.add(timings.preprocessMs.toDouble())
        state.inference.add(timings.inferenceMs.toDouble())
        state.postprocess.add(timings.postprocessMs.toDouble())
        state.total.add(timings.totalMs.toDouble())
        state.raw.add(snapshot.result.exerciseType)
        state.stable.add(snapshot.stableExercise)
        val selected = snapshot.result.selected
        if (selected != null && selected.exerciseType in YoloModelPolicy.supported && selected.source == DetectionSource.YOLO) {
            selected.confidence?.toDouble()?.takeIf { it.isFinite() && it in 0.0..1.0 }?.let(state.confidence::add)
        }
        if (expectedSessionExercise !in YoloModelPolicy.supported) {
            state.unknownSession = increment(state.unknownSession)
        } else {
            when {
                snapshot.stableExercise !in YoloModelPolicy.supported -> state.unknown = increment(state.unknown)
                snapshot.stableExercise == expectedSessionExercise -> state.matching = increment(state.matching)
                else -> state.mismatching = increment(state.mismatching)
            }
        }
    }

    @Synchronized
    fun finish(token: Long, endedAtEpochMs: Long, endedMonotonicMs: Long,
               expectedSessionExercise: ExerciseType, thermalStatus: Int): YoloSessionDiagnostics? {
        val result = snapshot(token, endedAtEpochMs, endedMonotonicMs, expectedSessionExercise, thermalStatus) ?: return null
        active = null
        return result
    }

    /** Read-only live aggregate; taking a snapshot never freezes or resets the active session. */
    @Synchronized
    fun snapshot(token: Long, observedAtEpochMs: Long, observedAtMonotonicMs: Long,
                 expectedSessionExercise: ExerciseType, thermalStatus: Int): YoloSessionDiagnostics? {
        val state = state(token) ?: return null
        state.observeThermal(thermalStatus)
        val duration = observedAtMonotonicMs.coerceAtLeast(state.monotonicStart) - state.monotonicStart
        val result = YoloSessionDiagnostics(
            modelStatus = state.status.name, contractStatus = state.contract.name,
            predictionEnabled = state.shadowEnabled && state.status == DetectorStatus.READY && state.contract == ExportContractVerification.VERIFIED,
            shadowRuntimeEnabled = state.shadowEnabled,
            totalFramesSeen = state.seen, admittedFrames = state.admitted, processedFrames = state.processed,
            activeDurationMs = duration,
            averageProcessedFps = if (state.processed > 0 && duration > 0) state.processed.toDouble() * 1000 / duration else null,
            averagePreprocessMs = state.preprocess.average, averageInferenceMs = state.inference.average,
            averagePostprocessMs = state.postprocess.average, averageTotalMs = state.total.average,
            confidenceSampleCount = state.confidence.count, averageConfidence = state.confidence.average,
            minConfidence = state.confidence.min, maxConfidence = state.confidence.max,
            maxThermalStatus = state.maxThermal, rawDetectionCounts = state.raw.frozen(),
            stableDetectionCounts = state.stable.frozen(),
            expectedSessionExercise = supportedOrUnknown(expectedSessionExercise).name,
            matchingStableFrames = state.matching, mismatchingStableFrames = state.mismatching,
            unknownStableFrames = state.unknown, unknownSessionFrames = state.unknownSession,
            cameraView = state.view.name, startedAt = state.epochStart,
            endedAt = observedAtEpochMs.coerceAtLeast(state.epochStart)
        )
        return result
    }

    private fun state(token: Long): State? = active?.takeIf { it.token == token }

    private class State(val token: Long, val epochStart: Long, val monotonicStart: Long, val view: CameraView,
                        var status: DetectorStatus, var contract: ExportContractVerification, var shadowEnabled: Boolean) {
        var seen = 0L; var admitted = 0L; var processed = 0L
        var matching = 0L; var mismatching = 0L; var unknown = 0L; var unknownSession = 0L
        var maxThermal: Int? = null
        val preprocess = Mean(); val inference = Mean(); val postprocess = Mean(); val total = Mean(); val confidence = Mean()
        val raw = Counts(); val stable = Counts()
        fun observeThermal(value: Int) {
            if (value in 0..6) maxThermal = maxOf(maxThermal ?: value, value)
        }
    }

    /** Incremental mean avoids an ever-growing latency sum and stores no per-frame samples. */
    private class Mean {
        var count = 0L; private set
        private var value = 0.0
        var min: Double? = null; private set
        var max: Double? = null; private set
        val average: Double? get() = value.takeIf { count > 0 }
        fun add(sample: Double) {
            count = increment(count)
            value += (sample - value) / count
            min = minOf(min ?: sample, sample); max = maxOf(max ?: sample, sample)
        }
    }

    private class Counts {
        private val values = linkedMapOf("SQUAT" to 0L, "SHOULDER_PRESS" to 0L, "UNKNOWN" to 0L)
        fun add(type: ExerciseType) { val key = supportedOrUnknown(type).name; values[key] = increment(values.getValue(key)) }
        fun frozen(): Map<String, Long> = Collections.unmodifiableMap(LinkedHashMap(values))
    }

    companion object {
        private fun increment(value: Long): Long = if (value < Long.MAX_VALUE) value + 1 else value
        private fun supportedOrUnknown(type: ExerciseType): ExerciseType =
            type.takeIf { it in YoloModelPolicy.supported } ?: ExerciseType.UNKNOWN
    }
}
