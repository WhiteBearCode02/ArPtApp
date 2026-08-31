package com.example.arptapp.domain.classifier

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class ExerciseClassifier {
    /**
     * 자동 분류 모델이 연결되기 전까지는 운동을 확정하지 않습니다.
     *
     * 이 메서드는 추후 시계열 분류 모델의 단일 진입점으로 사용합니다.
     * 모델과 특징 추출기가 없는 상태에서 임의의 종목을 반환하면 잘못된
     * Analyzer가 실행될 수 있으므로, 명시적으로 UNKNOWN을 반환합니다.
     */
    fun detectExercise(landmarks: List<NormalizedLandmark>): String {
        return "UNKNOWN"
    }
}
