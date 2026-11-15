package com.example.arptapp.domain.counter

import com.example.arptapp.domain.analyzer.ExerciseMotionPolicy

/** Monotonic camera timestamps only. Missing samples never stand in for a measured baseline.
 * A brief dropout preserves a cycle, but cannot add transition/stability frames or repetitions.
 * The same policy is used at the session boundary and independently for each curl arm.
 */
internal class MeasurementContinuity {
    data class Decision(val evaluable: Boolean, val resetPending: Boolean, val invalidDurationMs: Long)
    private var lastAt: Long? = null
    private var invalidSince: Long? = null
    private var expired = false

    fun reset() { lastAt = null; invalidSince = null; expired = false }

    fun observe(evaluable: Boolean, time: Long): Decision {
        val previous = lastAt
        if (time < 0 || previous?.let { time <= it } == true) {
            // Do not accept stale data or move the clock backwards.
            return Decision(false, true, 0)
        }
        val gap = previous?.let { time - it >= ExerciseMotionPolicy.MAX_FRAME_GAP_MS } == true
        lastAt = time
        if (!evaluable && invalidSince == null) invalidSince = time
        val duration = invalidSince?.let { time - it } ?: 0L
        val timeout = duration >= ExerciseMotionPolicy.MAX_FRAME_GAP_MS
        val reset = !expired && (gap || timeout)
        if (reset) expired = true
        if (evaluable) { invalidSince = null; expired = false }
        return Decision(evaluable, reset, duration)
    }
}
