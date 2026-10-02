package com.example.arptapp.domain.analyzer

import com.example.arptapp.domain.counter.ExerciseCounter
import org.junit.Assert.*
import org.junit.Test

class ExerciseCounterTest {
    @Test fun tooFastMotionDoesNotBecomeARepWhileStandingStill() {
        val counter = ExerciseCounter()
        repeat(3) { counter.processAngle("SQUAT", 85.0, it * 50L) }
        repeat(3) { counter.processAngle("SQUAT", 175.0, 150L + it * 50) }
        repeat(10) { counter.processAngle("SQUAT", 175.0, 900L + it * 100) }
        assertEquals(0, counter.getRepCount())
    }
    @Test fun onlyStableCompleteRepetitionsCount() {
        val counter = ExerciseCounter()
        repeat(3) { counter.processAngle("SQUAT", 85.0, it * 100L) }
        repeat(3) { counter.processAngle("SQUAT", 175.0, 900L + it * 100) }
        assertEquals(1, counter.getRepCount())
        assertEquals(85.0, counter.getCurrentMaxAngle(), 0.01)
    }
    @Test fun occlusionAbandonsPendingRepetition() {
        val counter = ExerciseCounter()
        repeat(3) { counter.processAngle("SQUAT", 85.0, it * 100L) }
        counter.invalidatePendingRep()
        repeat(3) { counter.processAngle("SQUAT", 175.0, 900L + it * 100) }
        assertEquals(0, counter.getRepCount())
    }
    @Test fun noCountOnNoiseOrInvalidAngle() {
        val counter = ExerciseCounter()
        repeat(10) { counter.processAngle("SQUAT", if (it % 2 == 0) 85.0 else 110.0, it * 100L) }
        counter.processAngle("SQUAT", Double.NaN)
        assertEquals(0, counter.getRepCount())
    }
}
