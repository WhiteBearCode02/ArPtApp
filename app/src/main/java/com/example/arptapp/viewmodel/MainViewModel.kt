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

    fun addRepRecord(maxBendAngle: Float, swayX: Float) {
        if (maxBendAngle.isFinite() && swayX.isFinite()) {
            records += RepRecord(maxBendAngle, abs(swayX))
        }
    }

    fun generateFinalReport(exerciseType: String): SessionReport {
        val normalizedType = exerciseType.uppercase()
        val scoreList = records.map { scoreRep(it, normalizedType) }
        val averageScore = scoreList.average().takeIf { it.isFinite() }?.toFloat() ?: 0f
        return SessionReport(
            exerciseType = normalizedType,
            totalReps = records.size,
            averageScore = averageScore,
            feedback = buildFeedback(normalizedType, averageScore)
        )
    }

    private fun scoreRep(record: RepRecord, exerciseType: String): Float {
        val (targetAngle, tolerance) = when (exerciseType) {
            "SHOULDER_PRESS" -> 90f to 25f
            else -> 90f to 20f
        }
        val angleScore = (100f - abs(record.maxBendAngle - targetAngle) / tolerance * 100f)
            .coerceIn(0f, 100f)
        val swayPenalty = (record.swayX / 3f * 25f).coerceIn(0f, 25f)
        return (angleScore - swayPenalty).coerceIn(0f, 100f)
    }

    private fun buildFeedback(exerciseType: String, averageScore: Float): String {
        if (records.isEmpty()) return "반복 기록이 없습니다. 카메라 앞에서 운동을 시작해 주세요."
        val averageSway = records.map { it.swayX }.average().toFloat()
        val target = if (exerciseType == "SHOULDER_PRESS") "팔꿈치" else "무릎"
        return when {
            averageSway > 2f -> "상체가 많이 흔들렸습니다. 코어를 고정하고 천천히 움직여 주세요."
            averageScore < 70f -> "$target 굽힘 각도를 기준 범위에 맞추고 동작을 천천히 반복해 주세요."
            averageScore < 90f -> "좋습니다. $target 각도와 몸의 중심을 조금 더 안정적으로 유지해 보세요."
            else -> "훌륭합니다. $target 각도와 중심이 안정적입니다."
        }
    }
}
