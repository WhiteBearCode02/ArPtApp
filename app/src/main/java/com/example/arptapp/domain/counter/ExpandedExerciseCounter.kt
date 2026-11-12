package com.example.arptapp.domain.counter

import com.example.arptapp.domain.analyzer.ExerciseMotionPolicy
import com.example.arptapp.domain.report.BodySide
import kotlin.math.abs
import kotlin.math.sign

data class MotionCompletion(val side: BodySide?, val peakAngle: Double,
    val outwardDurationMs: Long, val returnDurationMs: Long, val startedAtMs: Long)

/** ENGINEERING_INITIAL hysteresis. Counting is independent of form observations.
 * Curl arms own independent machines; simultaneous completions are returned separately, never lost.
 */
internal class ExpandedExerciseCounter(private val stableFrames: Int, private val minimumMs: Long) {
    private data class Cycle(var armed: Boolean = false, var turned: Boolean = false, var frames: Int = 0,
        var started: Long? = null, var turnAt: Long = 0, var peak: Double = 0.0, var direction: Double = 0.0,
        var lastAt: Long? = null, var baselineFrames: Int = 0)
    private var type: String? = null
    private val cycles = mutableMapOf<BodySide?, Cycle>()
    fun reset() { cycles.clear(); type = null }
    fun process(exercise: String, angle: Double?, left: Double?, right: Double?, time: Long): List<MotionCompletion> {
        val policy = ExerciseMotionPolicy.forExercise(exercise) ?: return emptyList()
        if (type != exercise) { reset(); type = exercise }
        val sides = if (exercise == "BICEP_CURL") listOf(BodySide.LEFT, BodySide.RIGHT) else listOf(null)
        return sides.mapNotNull { side ->
            val value = when (side) { BodySide.LEFT -> left; BodySide.RIGHT -> right; null -> angle }
            val previous = cycles.getOrPut(side) { Cycle() }
            val range = if (exercise == "SIDE_BEND") -ExerciseMotionPolicy.SIDE_BEND_MAX_MEASURABLE_DEG..ExerciseMotionPolicy.SIDE_BEND_MAX_MEASURABLE_DEG else 0.0..180.0
            if (value == null || !value.isFinite() || value !in range || time < 0 ||
                previous.lastAt?.let { time <= it || time - it > ExerciseMotionPolicy.MAX_FRAME_GAP_MS } == true) {
                cycles[side] = Cycle(); return@mapNotNull null
            }
            val c = previous
            c.lastAt = time
            val lateral = exercise == "SIDE_BEND"
            val extension = exercise == "TRICEPS_EXTENSION"
            val baseline = if (lateral) abs(value) <= policy.returnDeg else if (extension) value <= policy.turnDeg else value >= policy.returnDeg
            if (!c.armed) {
                c.frames = if (baseline) c.frames + 1 else 0
                if (c.frames >= stableFrames) { c.armed = true; c.frames = 0; c.peak = value }
                return@mapNotNull null
            }
            if (c.started == null && !baseline) { c.started = time; c.direction = sign(value) }
            if (c.started == null) return@mapNotNull null
            if (!c.turned && baseline) {
                c.baselineFrames++
                c.frames = 0
                if (c.baselineFrames >= stableFrames) {
                    c.started = null; c.frames = 0; c.baselineFrames = 0; c.peak = value; c.direction = 0.0
                }
                return@mapNotNull null // aborted outward motion cannot subsidize the next rep's duration
            }
            c.baselineFrames = 0
            if (lateral && abs(value) > policy.returnDeg && sign(value) != c.direction) {
                cycles[side] = Cycle(); return@mapNotNull null // no cross-side stitching
            }
            c.peak = if (lateral || extension) maxOf(abs(c.peak), abs(value)) else minOf(c.peak, value)
            val atTurn = if (lateral) abs(value) >= policy.turnDeg else if (extension) value >= policy.returnDeg else value <= policy.turnDeg
            if (!c.turned) {
                c.frames = if (atTurn) c.frames + 1 else 0
                if (c.frames >= stableFrames) { c.turned = true; c.frames = 0; c.turnAt = time }
                if (!extension || !c.turned) return@mapNotNull null
            } else if (!extension) {
                c.frames = if (baseline) c.frames + 1 else 0
                if (c.frames < stableFrames) return@mapNotNull null
            }
            val start = requireNotNull(c.started)
            val completion = if (time - start >= minimumMs) MotionCompletion(
                if (lateral) (if (c.direction < 0) BodySide.LEFT else BodySide.RIGHT) else side,
                c.peak, (c.turnAt - start).coerceAtLeast(0), (time - c.turnAt).coerceAtLeast(0), start) else null
            // Re-arm at baseline for every repetition; no top jitter duplicates and no fabricated initial half rep.
            cycles[side] = Cycle()
            completion
        }
    }
}
