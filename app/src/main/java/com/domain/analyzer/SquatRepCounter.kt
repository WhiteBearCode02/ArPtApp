package com.example.arptapp.domain.analyzer

/**
 * Frames from a single squat repetition are converted into a stable count.
 * A count is emitted only after the user remains in both the bottom and
 * standing positions for several consecutive valid frames.
 */
internal class SquatRepCounter(
    private val requiredStableFrames: Int = 3,
    private val minimumRepDurationMs: Long = 600L,
    private val downKneeAngle: Double = 105.0,
    private val upKneeAngle: Double = 160.0,
    private val downDescentRatio: Double = 1.0,
    private val upDescentRatio: Double = 1.2
) {
    private enum class State { STANDING, DOWN }

    private var state = State.STANDING
    private var downFrameCount = 0
    private var upFrameCount = 0
    private var downStartedAtMs = 0L
    var count: Int = 0
        private set

    fun update(kneeAngle: Double, descentRatio: Double, timestampMs: Long): Int {
        if (!kneeAngle.isFinite() || !descentRatio.isFinite()) return count

        when (state) {
            State.STANDING -> {
                if (kneeAngle <= downKneeAngle && descentRatio <= downDescentRatio) {
                    downFrameCount++
                    if (downFrameCount >= requiredStableFrames) {
                        state = State.DOWN
                        downStartedAtMs = timestampMs
                        upFrameCount = 0
                    }
                } else {
                    downFrameCount = 0
                }
            }

            State.DOWN -> {
                if (kneeAngle >= upKneeAngle && descentRatio >= upDescentRatio) {
                    upFrameCount++
                    val repDurationMs = timestampMs - downStartedAtMs
                    if (upFrameCount >= requiredStableFrames && repDurationMs >= minimumRepDurationMs) {
                        count++
                        state = State.STANDING
                        downFrameCount = 0
                        upFrameCount = 0
                    }
                } else {
                    upFrameCount = 0
                }
            }
        }
        return count
    }

    fun reset() {
        state = State.STANDING
        downFrameCount = 0
        upFrameCount = 0
        downStartedAtMs = 0L
        count = 0
    }
}
