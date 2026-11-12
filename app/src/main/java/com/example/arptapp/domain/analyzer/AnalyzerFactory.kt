package com.example.arptapp.domain.analyzer

import com.example.arptapp.domain.classifier.ExerciseType

/**
 * 매니저 역할: AI가 판단한 운동 종목에 맞는 전문 분석기를 가져옵니다.
 */
object AnalyzerFactory {
    fun getAnalyzer(exerciseType: ExerciseType): BaseExerciseAnalyzer? {
        return when (exerciseType) {
            ExerciseType.IDLE -> null
            ExerciseType.SQUAT -> SquatAnalyzer()
            ExerciseType.SHOULDER_PRESS -> ShoulderPressAnalyzer()
            ExerciseType.LUNGE -> LungeAnalyzer()
            ExerciseType.UNKNOWN -> null
            else -> MeasurementExerciseAnalyzer(exerciseType)
        }
    }
}

/** Legacy adapter only; live Dashboard supplies explicit camera view/aspect via measurement strategies. */
private class MeasurementExerciseAnalyzer(private val type: ExerciseType) : BaseExerciseAnalyzer {
    private val counter = com.example.arptapp.domain.counter.ExerciseCounter()
    private var evaluable = false
    override fun analyze(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>): Int {
        val points = landmarks.toAnalysisPose().landmarks
        val m = ExerciseMeasurements.measure(type.name, points, CameraView.FRONT) ?: return counter.getRepCount()
        evaluable = m.representative.isEvaluable
        counter.processMeasurement(type.name, m.representative.valueDeg, System.currentTimeMillis(), m.leftElbow, m.rightElbow)
        return counter.getRepCount()
    }
    override fun reset() { counter.resetSession(); evaluable = false }
    override fun isProperForm(): Boolean = evaluable
}
