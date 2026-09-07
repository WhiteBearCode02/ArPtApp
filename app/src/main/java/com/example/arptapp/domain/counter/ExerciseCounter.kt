package com.example.arptapp.domain.counter

import com.example.arptapp.data.model.StandardPose

/** Counts complete down/up repetitions for supported exercises. */
class ExerciseCounter {
    enum class State {
        UP,
        DOWN
    }

    private var activeExerciseType: String? = null
    private var currentState = State.UP
    private var repCount = 0
    private var currentMaxAngle = 0.0

    /**
     * Processes one frame's representative joint angle.
     * Returns true only on the transition that completes a repetition.
     */
    fun processAngle(exerciseType: String, angle: Double): Boolean {
        val normalizedType = exerciseType.uppercase()
        if (normalizedType != "SQUAT" && normalizedType != "SHOULDER_PRESS") return false
        if (!angle.isFinite()) return false

        if (activeExerciseType != normalizedType) {
            activeExerciseType = normalizedType
            currentState = State.UP
            currentMaxAngle = 0.0
        }

        return when (normalizedType) {
            "SQUAT" -> processSquatAngle(angle)
            "SHOULDER_PRESS" -> processShoulderPressAngle(angle)
            else -> false
        }
    }

    fun getRepCount(): Int = repCount

    fun getCurrentMaxAngle(): Double = currentMaxAngle

    fun getCurrentState(): State = currentState

    fun resetSession() {
        activeExerciseType = null
        currentState = State.UP
        repCount = 0
        currentMaxAngle = 0.0
    }

    private fun processSquatAngle(angle: Double): Boolean {
        return when (currentState) {
            State.UP -> {
                if (angle <= StandardPose.SQUAT_DOWN_THRESHOLD) {
                    currentState = State.DOWN
                    currentMaxAngle = angle
                }
                false
            }

            State.DOWN -> {
                if (angle < currentMaxAngle) currentMaxAngle = angle
                completeRepIf(angle >= StandardPose.SQUAT_UP_THRESHOLD)
            }
        }
    }

    private fun processShoulderPressAngle(angle: Double): Boolean {
        return when (currentState) {
            State.UP -> {
                if (angle <= StandardPose.SHOULDER_BOTTOM_THRESHOLD) {
                    currentState = State.DOWN
                    currentMaxAngle = angle
                }
                false
            }

            State.DOWN -> {
                if (angle > currentMaxAngle) currentMaxAngle = angle
                completeRepIf(angle >= StandardPose.SHOULDER_TOP_THRESHOLD)
            }
        }
    }

    private fun completeRepIf(isComplete: Boolean): Boolean {
        if (!isComplete) return false
        currentState = State.UP
        repCount++
        return true
    }
}
