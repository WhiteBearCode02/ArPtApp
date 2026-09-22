package com.example.arptapp.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelRepAnalysisTest {
    @Test
    fun createsOneAnalysisPerRecordedRepetition() {
        val viewModel = MainViewModel()
        viewModel.addRepRecord(1, 82.0, 0f)
        viewModel.addRepRecord(2, 88.0, 0f, listOf("ERROR_KNEE_VALGUS"))

        val analyses = viewModel.getRepAnalyses("SQUAT")

        assertEquals(2, analyses.size)
        assertEquals(1, analyses[0].repNumber)
        assertEquals(2, analyses[1].repNumber)
        assertEquals(100f, analyses[0].score)
        assertEquals(95f, analyses[1].score)
        assertTrue(analyses[1].detail.contains("무릎"))
        assertEquals(97, viewModel.generateFinalReport("SQUAT").averageScore)
    }

    @Test
    fun phoneAccelerationDoesNotChangePostureScore() {
        val viewModel = MainViewModel()
        viewModel.addRepRecord(1, 170.0, 0f)
        viewModel.addRepRecord(2, 170.0, 5f)

        val analyses = viewModel.getRepAnalyses("SHOULDER_PRESS")

        assertEquals(2, analyses.size)
        assertEquals(analyses[0].score, analyses[1].score)
        assertTrue(analyses[0].detail.contains("팔꿈치"))
    }

    @Test
    fun usesMeasuredPoseScoreWhenAvailable() {
        val viewModel = MainViewModel()
        viewModel.addRepRecord(1, 82.0, 0f, poseScore = 84f)

        assertEquals(84f, viewModel.getRepAnalyses("SQUAT").single().score)
        assertEquals(84, viewModel.generateFinalReport("SQUAT").averageScore)
    }
}
