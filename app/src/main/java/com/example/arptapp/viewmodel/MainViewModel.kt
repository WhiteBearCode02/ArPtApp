package com.example.arptapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.arptapp.model.RepRecord
import com.example.arptapp.model.RepAnalysis
import com.example.arptapp.model.SessionReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class MainViewModel : ViewModel() {
    private val records = mutableListOf<RepRecord>()
    private val _exerciseType = MutableStateFlow("IDLE")
    val exerciseType: StateFlow<String> = _exerciseType.asStateFlow()

    fun updateExerciseType(type: String) {
        _exerciseType.value = type.uppercase().let {
            if (it == "SQUAT" || it == "SHOULDER_PRESS") it else "IDLE"
        }
    }

    fun resetSession() {
        records.clear()
        _exerciseType.value = "IDLE"
    }

    fun addRepRecord(
        repNumber: Int,
        angle: Double,
        sway: Float,
        errorTags: List<String> = emptyList(),
        eccentricDurationMs: Long = 0L,
        concentricDurationMs: Long = 0L,
        poseScore: Float? = null
    ) {
        if (angle.isFinite() && sway.isFinite()) {
            records += RepRecord(
                repNumber = repNumber,
                maxAngle = angle,
                swayX = abs(sway),
                errorTags = errorTags.distinct(),
                eccentricDurationMs = eccentricDurationMs.coerceAtLeast(0L),
                concentricDurationMs = concentricDurationMs.coerceAtLeast(0L),
                poseScore = poseScore?.takeIf { it.isFinite() && it in 0f..100f }
            )
        }
    }

    fun getRepRecords(): List<RepRecord> = records.toList()

    fun getRepAnalyses(exerciseType: String): List<RepAnalysis> = records.mapIndexed { index, record ->
        RepAnalysis(
            repNumber = index + 1,
            score = scoreRep(record, exerciseType.uppercase()).toFloat(),
            detail = describeRep(record, exerciseType.uppercase())
        )
    }

    fun generateFinalReport(exerciseType: String): SessionReport {
        val normalizedType = exerciseType.uppercase()
        if (records.isEmpty()) {
            return SessionReport(normalizedType, 0, 0, "유효한 운동 데이터가 부족합니다.")
        }

        val averageScore = getRepAnalyses(normalizedType).map { it.score }.average().toInt()
        return SessionReport(
            exerciseType = normalizedType,
            totalReps = records.size,
            averageScore = averageScore,
            feedbackMessage = buildFeedback(normalizedType, averageScore)
        )
    }

    private fun scoreRep(record: RepRecord, exerciseType: String): Int {
        val angleScore = if (exerciseType == "SQUAT") {
            if (record.maxAngle <= 90.0) 100 else 70
        } else {
            if (record.maxAngle >= 160.0) 100 else 75
        }
        // swayX comes from the phone accelerometer, not the person's joints.
        // It must not be presented as a measure of body stability.
        val formErrorPenalty = record.errorTags.size * 5
        return ((record.poseScore?.toInt() ?: angleScore) - formErrorPenalty).coerceIn(0, 100)
    }

    private fun describeRep(record: RepRecord, exerciseType: String): String {
        val observations = mutableListOf<String>()
        observations += if (record.poseScore != null) {
            "표준 동작과의 관절 각도 비교 점수를 반영했어요."
        } else {
            "관절 각도 기준으로 추정한 점수예요."
        }
        if (exerciseType == "SQUAT") {
            observations += "가장 낮은 지점의 무릎 각도 약 ${record.maxAngle.toInt()}°가 측정됐어요."
            if (record.maxAngle > 90.0) {
                observations += "다음 회차에는 무리하지 않는 범위에서 동작 깊이를 일정하게 해 보세요."
            }
        } else if (exerciseType == "SHOULDER_PRESS") {
            observations += "팔을 올렸을 때 팔꿈치 각도 약 ${record.maxAngle.toInt()}°가 측정됐어요."
            if (record.maxAngle < 160.0) {
                observations += "다음 회차에는 팔을 올리는 범위를 천천히 확인해 보세요."
            }
        }
        if ("ERROR_KNEE_VALGUS" in record.errorTags) {
            observations += "무릎이 안쪽으로 모이는 움직임이 감지됐어요. 무릎 방향을 발끝과 맞춰 보세요."
        }
        if ("ERROR_FORWARD_LEAN" in record.errorTags) {
            observations += "상체가 앞으로 기우는 움직임이 감지됐어요. 가슴을 세우고 천천히 반복해 보세요."
        }
        if (record.errorTags.isEmpty()) {
            observations += "이 회차에서 별도 자세 오류 태그는 감지되지 않았어요."
        }
        return observations.joinToString(" ").ifBlank { "이 회차의 세부 관절 분석 데이터가 부족합니다." }
    }

    private fun buildFeedback(exerciseType: String, averageScore: Int): String {
        val errorFeedback = records
            .flatMap { it.errorTags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(2)
            .map { (tag, count) -> formatErrorFeedback(tag, count) }

        val scoreFeedback = when {
            averageScore >= 90 -> "전문가 수준의 완벽한 자세입니다!"
            averageScore >= 75 -> "안정적이지만 하강 시 미세한 흔들림이 있습니다."
            else -> "코어에 긴장을 유지하고 가동 범위를 일정하게 가져가세요."
        }
        return (errorFeedback + scoreFeedback).joinToString("\n")
    }

    private fun formatErrorFeedback(tag: String, count: Int): String = when (tag) {
        "ERROR_KNEE_VALGUS" ->
            "스쿼트 ${count}회에서 무릎이 안쪽으로 모이는 현상이 감지되었습니다. 발끝 방향으로 무릎을 열어주세요."
        "ERROR_FORWARD_LEAN" ->
            "스쿼트 ${count}회에서 상체 전방 경사가 감지되었습니다. 가슴을 세우고 코어를 고정해주세요."
        else -> "$tag 문제가 ${count}회 감지되었습니다. 자세를 천천히 교정해주세요."
    }
}
