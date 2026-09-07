package com.example.arptapp.domain.classifier

enum class ExerciseType(val displayName: String) {
    IDLE("대기 중"),
    UNKNOWN("운동 감지 중"),
    SQUAT("스쿼트"),
    SHOULDER_PRESS("숄더 프레스"),
    LUNGE("런지")
}
