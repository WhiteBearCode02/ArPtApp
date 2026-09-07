package com.example.arptapp.domain.analyzer

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * 모든 운동 분석기가 반드시 지켜야 할 규칙입니다.
 * 인터페이스를 사용하면 DashboardActivity는 구체적인 운동 내용을 몰라도
 * "분석해줘!"라는 명령어 하나로 모든 운동을 제어할 수 있습니다.
 */
interface BaseExerciseAnalyzer {
    // 1. 랜드마크 데이터를 받아서 분석하고, 현재까지의 총 횟수를 반환합니다.
    fun analyze(landmarks: List<NormalizedLandmark>): Int

    // 2. 운동을 처음부터 다시 시작할 때 데이터를 초기화합니다.
    fun reset()

    // 3. 현재 운동이 '내려간 상태(Down)'인지 확인하는 상태값입니다 (피드백용).
    fun isProperForm(): Boolean
}