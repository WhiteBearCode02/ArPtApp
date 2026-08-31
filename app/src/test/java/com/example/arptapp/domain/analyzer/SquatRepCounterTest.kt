package com.example.arptapp.domain.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class SquatRepCounterTest {
    @Test
    fun countsOnlyAfterStableDescentAndAscent() {
        val counter = SquatRepCounter(requiredStableFrames = 3, minimumRepDurationMs = 600)

        repeat(3) { frame -> counter.update(95.0, 0.9, frame * 100L) }
        repeat(5) { frame -> counter.update(170.0, 1.3, 800L + frame * 100L) }

        assertEquals(1, counter.count)
    }

    @Test
    fun ignoresThresholdNoiseAndFastPartialMotion() {
        val counter = SquatRepCounter(requiredStableFrames = 3, minimumRepDurationMs = 600)

        counter.update(100.0, 0.9, 0)
        counter.update(110.0, 1.1, 100)
        counter.update(100.0, 0.9, 200)
        counter.update(170.0, 1.3, 300)

        assertEquals(0, counter.count)
    }
}
