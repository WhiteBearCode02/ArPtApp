package com.example.arptapp.ui.camera

import com.example.arptapp.domain.analyzer.CameraView
import com.example.arptapp.domain.classifier.ExerciseClassifier
import com.example.arptapp.domain.detection.*
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

data class LandmarkDetectionFrame(val landmarks: List<NormalizedLandmark>, val view: CameraView, val aspectRatio: Double)

/** Primary adapter retains the existing classifier, thresholds and stabilization. No fabricated confidence. */
class LandmarkExerciseDetector(private val classifier: ExerciseClassifier) : ExerciseDetector<LandmarkDetectionFrame> {
    override fun initialize() = ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.LANDMARK_FALLBACK)
    override fun detect(frame: LandmarkDetectionFrame): ExerciseDetectionResult {
        val type = classifier.detectExercise(frame.landmarks, frame.view, frame.aspectRatio)
        return ExerciseDetectionResult(DetectorStatus.READY, DetectionSource.LANDMARK_FALLBACK,
            ExerciseDetection(type, null, DetectionSource.LANDMARK_FALLBACK))
    }
    override fun close() { classifier.reset() }
}
