package com.example.arptapp.viewmodel

import androidx.lifecycle.ViewModel
import com.example.arptapp.model.RepRecord
import com.example.arptapp.model.RepAnalysis
import com.example.arptapp.model.SessionReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import com.example.arptapp.domain.report.RepReportAnalyzer

/**
 * 운동 세션의 화면 상태와 수집 기록만 소유합니다.
 * 기록은 복사본으로 공개하고, 점수·코칭 정책은 RepReportAnalyzer에 위임합니다.
 * 사용자 데이터는 이 객체에서 네트워크/영구 저장하지 않습니다.
 */
class MainViewModel : ViewModel() {
    private val reportAnalyzer = RepReportAnalyzer()
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

    fun getRepAnalyses(exerciseType: String): List<RepAnalysis> =
        reportAnalyzer.analyze(records, exerciseType)

    fun generateFinalReport(exerciseType: String): SessionReport =
        reportAnalyzer.report(records, exerciseType)
}
