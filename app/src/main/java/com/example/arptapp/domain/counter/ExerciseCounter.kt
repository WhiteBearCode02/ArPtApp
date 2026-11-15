package com.example.arptapp.domain.counter

import com.example.arptapp.data.model.StandardPose

/** Counts complete down/up repetitions for supported exercises. */
class ExerciseCounter(private val requiredStableFrames: Int = 3, private val minimumRepDurationMs: Long = 600L) {
    init { require(requiredStableFrames > 0 && minimumRepDurationMs >= 0) }
    private val expanded = ExpandedExerciseCounter(requiredStableFrames, minimumRepDurationMs)
    private val continuity = MeasurementContinuity()
    /** Operational result lets the camera reset analysis at exactly the same boundary as counting. */
    data class FrameResult(val completions: List<CompletedRep>, val evaluable: Boolean,
                           val pendingReset: Boolean, val invalidDurationMs: Long)
    data class CompletedRep(val repNumber: Int, val activeSide: com.example.arptapp.domain.report.BodySide?,
                            val peakAngle: Double, val outwardDurationMs: Long, val returnDurationMs: Long,
                            val motionStartedAtMs: Long? = null)
    enum class State {
        UP,
        DOWN
    }

    private var activeExerciseType: String? = null
    private var currentState = State.UP
    private var repCount = 0
    private var currentMaxAngle = 0.0
    private var upStartedAtMs = 0L
    private var downStartedAtMs = 0L
    private var lastEccentricDurationMs = 0L
    private var lastConcentricDurationMs = 0L
    private var transitionFrames = 0

    /** Abandon an incomplete repetition after occlusion; preserve completed counts. */
    fun invalidatePendingRep() {
        continuity.reset()
        clearPendingRep()
    }

    private fun clearPendingRep() {
        expanded.reset()
        currentState = State.UP
        currentMaxAngle = 0.0
        transitionFrames = 0
    }

    /**
     * Processes one frame's representative joint angle.
     * Returns true only on the transition that completes a repetition.
     */
    fun processAngle(
        exerciseType: String,
        angle: Double,
        timestampMs: Long = System.currentTimeMillis()
    ): Boolean {
        return processMeasurement(exerciseType, angle, timestampMs).isNotEmpty()
    }

    private fun processLegacyAngle(type: String, angle: Double, timestampMs: Long): Boolean {
        return when (type) {
            "SQUAT" -> processSquatAngle(angle, timestampMs)
            "SHOULDER_PRESS" -> processShoulderPressAngle(angle, timestampMs)
            else -> false
        }
    }

    fun getRepCount(): Int = repCount

    /** Camera path: explicit bilateral scalars are mandatory for alternating curls. */
    fun processMeasurement(exerciseType: String, angle: Double?, timestampMs: Long,
                           leftElbow: Double? = null, rightElbow: Double? = null): List<CompletedRep> =
        processFrame(exerciseType, angle, timestampMs, leftElbow, rightElbow).completions

    /** Accept EVERY camera sample, including missing-person/null measurements. Invalid samples
     * never count; only expiry resets unfinished work. Completed session counts are untouched.
     */
    fun processFrame(exerciseType: String, angle: Double?, timestampMs: Long,
                     leftElbow: Double? = null, rightElbow: Double? = null): FrameResult {
        val type = exerciseType.uppercase()
        if (activeExerciseType != type) {
            invalidatePendingRep(); continuity.reset()
            activeExerciseType = type
            upStartedAtMs = timestampMs
        }
        val supported = type == "SQUAT" || type == "SHOULDER_PRESS" ||
            com.example.arptapp.domain.analyzer.ExerciseMotionPolicy.forExercise(type) != null
        fun usable(value: Double?) = value != null && value.isFinite() && value in
            (if (type == "SIDE_BEND") -com.example.arptapp.domain.analyzer.ExerciseMotionPolicy.SIDE_BEND_MAX_MEASURABLE_DEG..
                com.example.arptapp.domain.analyzer.ExerciseMotionPolicy.SIDE_BEND_MAX_MEASURABLE_DEG else 0.0..180.0)
        val valid = supported && if (type == "BICEP_CURL") usable(leftElbow) || usable(rightElbow) else usable(angle)
        val decision = continuity.observe(valid, timestampMs)
        if (decision.resetPending) clearPendingRep()
        fun result(completions: List<CompletedRep> = emptyList()) = FrameResult(completions,
            decision.evaluable, decision.resetPending, decision.invalidDurationMs)
        // Curl arms have independent clocks: even when both are absent, advance their dropout
        // timers. Never supply the representative value as a fabricated left/right arm.
        if (!decision.evaluable) {
            if (type == "BICEP_CURL") expanded.process(type, null, null, null, timestampMs)
            return result()
        }
        if (type == "SQUAT" || type == "SHOULDER_PRESS") {
            val completed = processLegacyAngle(type, requireNotNull(angle), timestampMs)
            return result(if (completed) listOf(CompletedRep(repCount, null, currentMaxAngle,
                lastEccentricDurationMs, lastConcentricDurationMs)) else emptyList())
        }
        return result(expanded.process(type, angle, leftElbow, rightElbow, timestampMs).map { completion ->
            repCount++; currentMaxAngle = completion.peakAngle
            lastEccentricDurationMs = completion.outwardDurationMs
            lastConcentricDurationMs = completion.returnDurationMs
            CompletedRep(repCount, completion.side, completion.peakAngle,
                completion.outwardDurationMs, completion.returnDurationMs, completion.startedAtMs)
        })
    }

    /** Scalar read-only snapshot, consumed only by DEBUG presentation; not a persistence API. */
    internal fun diagnosticPhase(): String = if (activeExerciseType in listOf("SQUAT", "SHOULDER_PRESS"))
        if (currentState == State.DOWN) "TURNED" else "WAIT_TURN ($transitionFrames/$requiredStableFrames)"
        else expanded.diagnosticPhase()

    fun getCurrentMaxAngle(): Double = currentMaxAngle

    fun getCurrentState(): State = currentState

    fun getLastEccentricDurationMs(): Long = lastEccentricDurationMs

    fun getLastConcentricDurationMs(): Long = lastConcentricDurationMs

    fun resetSession() {
        continuity.reset()
        expanded.reset()
        activeExerciseType = null
        currentState = State.UP
        repCount = 0
        currentMaxAngle = 0.0
        upStartedAtMs = 0L
        downStartedAtMs = 0L
        lastEccentricDurationMs = 0L
        lastConcentricDurationMs = 0L
        transitionFrames = 0
    }

    private fun processSquatAngle(angle: Double, timestampMs: Long): Boolean {
        return when (currentState) {
            State.UP -> {
                transitionFrames = if (angle <= StandardPose.SQUAT_DOWN_THRESHOLD) transitionFrames + 1 else 0
                if (transitionFrames >= requiredStableFrames) {
                    currentState = State.DOWN
                    currentMaxAngle = angle
                    downStartedAtMs = timestampMs
                    lastEccentricDurationMs = (downStartedAtMs - upStartedAtMs).coerceAtLeast(0L)
                    transitionFrames = 0
                }
                false
            }

            State.DOWN -> {
                if (angle < currentMaxAngle) currentMaxAngle = angle
                completeRepIf(angle >= StandardPose.SQUAT_UP_THRESHOLD, timestampMs)
            }
        }
    }

    private fun processShoulderPressAngle(angle: Double, timestampMs: Long): Boolean {
        return when (currentState) {
            State.UP -> {
                transitionFrames = if (angle <= StandardPose.SHOULDER_BOTTOM_THRESHOLD) transitionFrames + 1 else 0
                if (transitionFrames >= requiredStableFrames) {
                    currentState = State.DOWN
                    currentMaxAngle = angle
                    downStartedAtMs = timestampMs
                    lastEccentricDurationMs = (downStartedAtMs - upStartedAtMs).coerceAtLeast(0L)
                    transitionFrames = 0
                }
                false
            }

            State.DOWN -> {
                if (angle > currentMaxAngle) currentMaxAngle = angle
                completeRepIf(angle >= StandardPose.SHOULDER_TOP_THRESHOLD, timestampMs)
            }
        }
    }

    private fun completeRepIf(isComplete: Boolean, timestampMs: Long): Boolean {
        transitionFrames = if (isComplete) transitionFrames + 1 else 0
        if (transitionFrames < requiredStableFrames) return false
        if (timestampMs - downStartedAtMs < minimumRepDurationMs) {
            invalidatePendingRep()
            upStartedAtMs = timestampMs
            return false
        }
        lastConcentricDurationMs = (timestampMs - downStartedAtMs).coerceAtLeast(0L)
        currentState = State.UP
        repCount++
        upStartedAtMs = timestampMs
        transitionFrames = 0
        return true
    }
}
