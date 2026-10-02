package com.example.arptapp.domain.counter

import com.example.arptapp.data.model.StandardPose

/** Counts complete down/up repetitions for supported exercises. */
class ExerciseCounter(private val requiredStableFrames: Int = 3, private val minimumRepDurationMs: Long = 600L) {
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
        val normalizedType = exerciseType.uppercase()
        if (normalizedType != "SQUAT" && normalizedType != "SHOULDER_PRESS") return false
        if (!angle.isFinite() || angle !in 0.0..180.0) {
            invalidatePendingRep()
            return false
        }

        if (activeExerciseType != normalizedType) {
            activeExerciseType = normalizedType
            currentState = State.UP
            currentMaxAngle = 0.0
            transitionFrames = 0
            upStartedAtMs = timestampMs
        }

        return when (normalizedType) {
            "SQUAT" -> processSquatAngle(angle, timestampMs)
            "SHOULDER_PRESS" -> processShoulderPressAngle(angle, timestampMs)
            else -> false
        }
    }

    fun getRepCount(): Int = repCount

    fun getCurrentMaxAngle(): Double = currentMaxAngle

    fun getCurrentState(): State = currentState

    fun getLastEccentricDurationMs(): Long = lastEccentricDurationMs

    fun getLastConcentricDurationMs(): Long = lastConcentricDurationMs

    fun resetSession() {
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
