package com.example.arptapp.domain.report

import com.example.arptapp.domain.analyzer.CameraView

/** Synthetic scalar fixtures, NOT observations from a real person or clinical reference motions. */
object RepScenarioFixtures {
    val squatAngles = listOf(170.0, 150.0, 130.0, 110.0, 100.0, 80.0, 81.0, 82.0, 83.0, 100.0, 120.0, 140.0, 160.0, 170.0)
    fun squat(left: Set<Int> = emptySet(), right: Set<Int> = emptySet(), ratio: Double = 0.17,
              difference: Double = 0.0, view: CameraView = CameraView.FRONT, lean: Double? = null): StructuredRepAnalysis {
        val engine = RepAnalysisEngine()
        squatAngles.forEachIndexed { i, a -> engine.observe(RepFrameFeatures(10_000 + i * 100L, "SQUAT", view, a,
            leftKnee = a, rightKnee = a - difference, torsoLean = lean,
            leftKneeValgusObserved = i in left, rightKneeValgusObserved = i in right,
            leftKneeValgusRatio = if (i in left) ratio else 0.0, rightKneeValgusRatio = if (i in right) ratio else 0.0)) }
        return engine.complete(3, 78f)!!
    }
    fun press(angles: List<Double> = listOf(80.0, 80.0, 80.0, 100.0, 120.0, 140.0, 160.0, 170.0, 170.0, 170.0),
              difference: Double = 30.0, view: CameraView = CameraView.FRONT): StructuredRepAnalysis {
        val engine = RepAnalysisEngine()
        angles.forEachIndexed { i, a -> engine.observe(RepFrameFeatures(20_000 + i * 100L, "SHOULDER_PRESS", view, a,
            leftElbow = a, rightElbow = a - difference)) }
        return engine.complete(1, 82f)!!
    }
}
