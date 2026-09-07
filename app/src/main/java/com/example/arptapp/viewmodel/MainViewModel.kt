package com.example.arptapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.arptapp.model.RepRecord
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

    fun addRepRecord(repNumber: Int, angle: Double, sway: Float) {
        if (angle.isFinite() && sway.isFinite()) {
            records += RepRecord(repNumber, angle, abs(sway))
        }
    }

    fun generateFinalReport(exerciseType: String): SessionReport {
        val normalizedType = exerciseType.uppercase()
        if (records.isEmpty()) {
            return SessionReport(normalizedType, 0, 0, "유효한 운동 데이터가 부족합니다.")
        }

        val averageScore = records.map { scoreRep(it, normalizedType) }.average().toInt()
        return SessionReport(
            exerciseType = normalizedType,
            totalReps = records.size,
            averageScore = averageScore,
            feedbackMessage = buildFeedback(normalizedType, averageScore)
        )
    }

    private fun scoreRep(record: RepRecord, exerciseType: String): Int {
        val baseScore = if (exerciseType == "SQUAT") {
            if (record.maxAngle <= 90.0) 100 else 70
        } else {
            if (record.maxAngle >= 160.0) 100 else 75
        }
        val stabilityPenalty = (record.swayX * 5f).toInt()
        return (baseScore - stabilityPenalty).coerceIn(0, 100)
    }

    private fun buildFeedback(exerciseType: String, averageScore: Int): String {
        return when {
            averageScore >= 90 -> "전문가 수준의 완벽한 자세입니다!"
            averageScore >= 75 -> "안정적이지만 하강 시 미세한 흔들림이 있습니다."
            else -> "코어에 긴장을 유지하고 가동 범위를 일정하게 가져가세요."
        }
    }
}
