package com.example.arptapp.domain.analyzer

/**
 * 매니저 역할: AI가 판단한 운동 종목에 맞는 전문 분석기를 가져옵니다.
 */
object AnalyzerFactory {
    fun getAnalyzer(exerciseType: String): BaseExerciseAnalyzer {
        return when (exerciseType) {
            "SQUAT" -> SquatAnalyzer() // 스쿼트 트레이너 호출
            else -> object : BaseExerciseAnalyzer { // 대기 중일 때의 기본 동작
                override fun analyze(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) = 0
                override fun reset() {}
                override fun isProperForm() = true
            }
        }
    }
}
