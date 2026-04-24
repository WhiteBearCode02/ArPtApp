package com.example.arptapp.domain.classifier

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class ExerciseClassifier {
    // 30프레임(약 1초)의 데이터를 담을 윈도우 버퍼
    private val frameBuffer = mutableListOf<FloatArray>()

    fun detectExercise(landmarks: List<NormalizedLandmark>): String {
        // [1] 현재 프레임의 99개 좌표(33관절 * x,y,z)를 추출
        val features = extractFeatures(landmarks)
        frameBuffer.add(features)
        
        // [2] 30프레임이 쌓였을 때만 AI 모델 추론 실행
        if (frameBuffer.size == 30) {
            val result = runInference(frameBuffer) // TFLite 모델 실행부
            frameBuffer.clear() // 분석 후 버퍼 초기화
            return result // "SQUAT", "LUNGE" 등 반환
        }
        return "READY" // 데이터 수집 중
    }
}