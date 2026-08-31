package com.example.arptapp.domain.analyzer

internal class LungeRepCounter(
    private val requiredStableFrames: Int = 3,
    private val minimumRepDurationMs: Long = 600L
) {
    private enum class State { STANDING, LUNGE }

    private var state = State.STANDING
    private var lungeFrames = 0
    private var standingFrames = 0
    private var lungeStartedAtMs = 0L
    var count = 0
        private set

    fun update(frontKneeAngle: Double, backKneeAngle: Double, timestampMs: Long): Int {
        if (!frontKneeAngle.isFinite() || !backKneeAngle.isFinite()) return count

        when (state) {
            State.STANDING -> {
                if (frontKneeAngle <= 110.0 && backKneeAngle >= 125.0) {
                    lungeFrames++
                    if (lungeFrames >= requiredStableFrames) {
                        state = State.LUNGE
                        lungeStartedAtMs = timestampMs
                        standingFrames = 0
                    }
                } else {
                    lungeFrames = 0
                }
            }

            State.LUNGE -> {
                if (frontKneeAngle >= 160.0 && backKneeAngle >= 160.0) {
                    standingFrames++
                    if (standingFrames >= requiredStableFrames && timestampMs - lungeStartedAtMs >= minimumRepDurationMs) {
                        count++
                        state = State.STANDING
                        lungeFrames = 0
                        standingFrames = 0
                    }
                } else {
                    standingFrames = 0
                }
            }
        }
        return count
    }

    fun reset() {
        state = State.STANDING
        lungeFrames = 0
        standingFrames = 0
        lungeStartedAtMs = 0L
        count = 0
    }
}
