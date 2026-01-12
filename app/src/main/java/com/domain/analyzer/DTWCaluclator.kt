package com.example.arptapp.domain.analyzer

import kotlin.math.min
import kotlin.math.sqrt
import kotlin.math.pow

/**
 * Dynamic Time Warping 알고리즘을 사용한 포즈 유사도 계산기
 * 
 * DTW는 두 시계열 데이터 간의 유사도를 측정하는 알고리즘으로,
 * 사용자의 운동 자세와 표준 자세를 비교하여 정확도를 계산합니다.
 */
class DTWCalculator {
    
    companion object {
        // 운동별 가중치 맵
        private val EXERCISE_WEIGHTS = mapOf(
            "SQUAT" to mapOf(
                "hip_angle" to 1.5f,      // 엉덩이 각도 (중요)
                "knee_angle" to 2.0f,     // 무릎 각도 (가장 중요)
                "ankle_angle" to 1.0f,    // 발목 각도
                "back_angle" to 1.5f      // 등 각도 (자세)
            ),
            "PUSHUP" to mapOf(
                "elbow_angle" to 2.0f,
                "shoulder_angle" to 1.5f,
                "back_angle" to 1.5f
            ),
            "PLANK" to mapOf(
                "back_angle" to 2.0f,
                "hip_angle" to 1.5f,
                "shoulder_angle" to 1.0f
            )
        )
        
        // DTW 거리의 최대값 (정규화용)
        private const val MAX_DTW_DISTANCE = 1000f
        
        // 유사도 점수 범위
        private const val MIN_SCORE = 0f
        private const val MAX_SCORE = 100f
    }
    
    /**
     * 운동 종류에 따른 가중치 반환
     */
    fun getExerciseWeights(exerciseType: String): Map<String, Float> {
        return EXERCISE_WEIGHTS[exerciseType.uppercase()] ?: mapOf(
            "default" to 1.0f
        )
    }
    
    /**
     * DTW 거리 계산 (메인 함수)
     * 
     * @param userSequence 사용자의 포즈 시퀀스 (각 프레임의 각도 배열)
     * @param standardSequence 표준 포즈 시퀀스
     * @param weights 각 관절의 가중치
     * @return DTW 거리 (낮을수록 유사함)
     */
    fun calculateDTWDistance(
        userSequence: List<FloatArray>,
        standardSequence: List<FloatArray>,
        weights: Map<String, Float>
    ): Float {
        if (userSequence.isEmpty() || standardSequence.isEmpty()) {
            return MAX_DTW_DISTANCE
        }
        
        val n = userSequence.size
        val m = standardSequence.size
        
        // DTW 매트릭스 초기화
        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        dtw[0][0] = 0f
        
        // DTW 알고리즘 실행
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = calculateFrameDistance(
                    userSequence[i - 1],
                    standardSequence[j - 1],
                    weights
                )
                
                dtw[i][j] = cost + minOf(
                    dtw[i - 1][j],     // 삽입
                    dtw[i][j - 1],     // 삭제
                    dtw[i - 1][j - 1]  // 매칭
                )
            }
        }
        
        // 정규화된 DTW 거리 반환
        return dtw[n][m] / maxOf(n, m)
    }
    
    /**
     * 두 프레임 간의 거리 계산 (가중치 적용)
     */
    private fun calculateFrameDistance(
        frame1: FloatArray,
        frame2: FloatArray,
        weights: Map<String, Float>
    ): Float {
        if (frame1.size != frame2.size) {
            return Float.MAX_VALUE
        }
        
        var totalDistance = 0f
        var totalWeight = 0f
        
        frame1.indices.forEach { i ->
            val weight = weights.values.elementAtOrNull(i) ?: 1.0f
            val diff = frame1[i] - frame2[i]
            totalDistance += weight * diff * diff
            totalWeight += weight
        }
        
        return if (totalWeight > 0) {
            sqrt(totalDistance / totalWeight)
        } else {
            sqrt(totalDistance)
        }
    }
    
    /**
     * 유클리드 거리 계산 (간단한 버전)
     */
    fun calculateEuclideanDistance(
        userPose: FloatArray,
        standardPose: FloatArray
    ): Float {
        if (userPose.size != standardPose.size) {
            return Float.MAX_VALUE
        }
        
        var sum = 0f
        for (i in userPose.indices) {
            val diff = userPose[i] - standardPose[i]
            sum += diff * diff
        }
        
        return sqrt(sum)
    }
    
    /**
     * DTW 거리를 0~100 점수로 변환
     * 
     * @param dtwDistance DTW 거리값
     * @return 0~100 사이의 점수 (높을수록 좋음)
     */
    fun convertToScore(dtwDistance: Float): Float {
        // DTW 거리를 역으로 변환 (거리가 클수록 점수 낮음)
        val normalizedDistance = (dtwDistance / MAX_DTW_DISTANCE).coerceIn(0f, 1f)
        
        // 0~100 점수로 변환 (비선형 변환으로 민감도 조정)
        val score = (1 - normalizedDistance.pow(0.5f)) * MAX_SCORE
        
        return score.coerceIn(MIN_SCORE, MAX_SCORE)
    }
    
    /**
     * 실시간 프레임별 점수 계산
     */
    fun calculateFrameScore(
        userAngles: FloatArray,
        standardAngles: FloatArray,
        weights: Map<String, Float>
    ): Float {
        val distance = calculateFrameDistance(userAngles, standardAngles, weights)
        
        // 프레임 거리를 0~100 점수로 변환 (임계값 기반)
        val threshold = 30f // 각도 차이 임계값
        val normalizedDistance = (distance / threshold).coerceIn(0f, 1f)
        
        return ((1 - normalizedDistance) * MAX_SCORE).coerceIn(MIN_SCORE, MAX_SCORE)
    }
    
    /**
     * 시퀀스의 평균 점수 계산
     */
    fun calculateAverageScore(
        userSequence: List<FloatArray>,
        standardSequence: List<FloatArray>,
        weights: Map<String, Float>
    ): Float {
        if (userSequence.isEmpty() || standardSequence.isEmpty()) {
            return 0f
        }
        
        // 표준 시퀀스를 사용자 시퀀스 길이에 맞게 리샘플링
        val resampledStandard = resampleSequence(standardSequence, userSequence.size)
        
        var totalScore = 0f
        
        userSequence.indices.forEach { i ->
            val score = calculateFrameScore(
                userSequence[i],
                resampledStandard[i],
                weights
            )
            totalScore += score
        }
        
        return totalScore / userSequence.size
    }
    
    /**
     * 시퀀스 리샘플링 (보간법 사용)
     */
    private fun resampleSequence(
        sequence: List<FloatArray>,
        targetSize: Int
    ): List<FloatArray> {
        if (sequence.isEmpty() || targetSize <= 0) {
            return emptyList()
        }
        
        if (sequence.size == targetSize) {
            return sequence
        }
        
        val result = mutableListOf<FloatArray>()
        val ratio = sequence.size.toFloat() / targetSize
        
        for (i in 0 until targetSize) {
            val index = (i * ratio).toInt().coerceIn(0, sequence.size - 1)
            result.add(sequence[index])
        }
        
        return result
    }
    
    /**
     * 각도 차이 계산 (순환 각도 고려)
     */
    fun calculateAngleDifference(angle1: Float, angle2: Float): Float {
        var diff = kotlin.math.abs(angle1 - angle2)
        if (diff > 180f) {
            diff = 360f - diff
        }
        return diff
    }
    
    /**
     * 관절별 정확도 분석
     */
    fun analyzeJointAccuracy(
        userAngles: FloatArray,
        standardAngles: FloatArray,
        jointNames: List<String>
    ): Map<String, Float> {
        if (userAngles.size != standardAngles.size || 
            userAngles.size != jointNames.size) {
            return emptyMap()
        }
        
        val accuracyMap = mutableMapOf<String, Float>()
        
        jointNames.indices.forEach { i ->
            val diff = calculateAngleDifference(userAngles[i], standardAngles[i])
            val accuracy = ((180f - diff) / 180f * 100f).coerceIn(0f, 100f)
            accuracyMap[jointNames[i]] = accuracy
        }
        
        return accuracyMap
    }
}