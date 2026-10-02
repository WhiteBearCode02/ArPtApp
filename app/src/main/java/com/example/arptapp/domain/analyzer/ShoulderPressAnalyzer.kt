package com.example.arptapp.domain.analyzer

import com.example.arptapp.domain.counter.ExerciseCounter
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/** Compatibility adapter using the same measurement and counter as the camera. */
class ShoulderPressAnalyzer : BaseExerciseAnalyzer {
    private val counter = ExerciseCounter()
    private var evaluable = false
    override fun analyze(landmarks: List<NormalizedLandmark>): Int {
        val measurement = PoseAngleExtractor.measurePose("SHOULDER_PRESS", landmarks.toAnalysisPose())
        evaluable = measurement.isEvaluable
        measurement.valueDeg?.let { counter.processAngle("SHOULDER_PRESS", it) } ?: counter.invalidatePendingRep()
        return counter.getRepCount()
    }
    override fun reset() { counter.resetSession(); evaluable = false }
    override fun isProperForm(): Boolean = evaluable
}
