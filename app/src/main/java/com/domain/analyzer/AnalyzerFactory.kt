package com.example.arptapp.domain.analyzer

import com.example.arptapp.domain.classifier.ExerciseType

/**
 * 매니저 역할: AI가 판단한 운동 종목에 맞는 전문 분석기를 가져옵니다.
 */
object AnalyzerFactory {
    fun getAnalyzer(exerciseType: ExerciseType): BaseExerciseAnalyzer? {
        return when (exerciseType) {
            ExerciseType.SQUAT -> SquatAnalyzer()
            ExerciseType.LUNGE -> LungeAnalyzer()
            ExerciseType.UNKNOWN -> null
        }
    }
}
