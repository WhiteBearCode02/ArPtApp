package com.example.arptapp.domain.analyzer

import org.junit.Assert.assertEquals
import org.junit.Test

class DTWCalculatorTest {
    @Test
    fun givesPerfectScoreToAnIdenticalSequence() {
        val calculator = DTWCalculator()
        val sequence = listOf(
            floatArrayOf(180f, 180f, 180f, 180f),
            floatArrayOf(90f, 90f, 90f, 90f),
            floatArrayOf(180f, 180f, 180f, 180f)
        )

        val score = calculator.calculateAverageScore(
            userSequence = sequence,
            standardSequence = sequence,
            weights = calculator.getExerciseWeights("SQUAT")
        )

        assertEquals(100f, score, 0.001f)
    }
}
