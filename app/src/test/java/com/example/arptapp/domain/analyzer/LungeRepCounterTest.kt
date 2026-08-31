package com.example.arptapp.domain.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class LungeRepCounterTest {
    @Test
    fun countsStableLungeAfterReturningToStanding() {
        val counter = LungeRepCounter(requiredStableFrames = 3, minimumRepDurationMs = 600L)

        repeat(3) { frame -> counter.update(100.0, 140.0, frame * 100L) }
        repeat(3) { frame -> counter.update(170.0, 170.0, 800L + frame * 100L) }

        assertEquals(1, counter.count)
    }
}
