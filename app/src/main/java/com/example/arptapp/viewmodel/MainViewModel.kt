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
import com.example.arptapp.domain.report.*
import com.example.arptapp.domain.classifier.ExerciseType
import com.example.arptapp.domain.session.SessionExerciseLock

/**
 * 운동 세션의 화면 상태와 수집 기록만 소유합니다.
 * 기록은 복사본으로 공개하고, 점수·코칭 정책은 RepReportAnalyzer에 위임합니다.
 * 사용자 데이터는 이 객체에서 네트워크/영구 저장하지 않습니다.
 */
class MainViewModel : ViewModel() {
    private val reportAnalyzer = RepReportAnalyzer()
    private val sessionLock = SessionExerciseLock()
    val sessionExerciseType: ExerciseType get() = sessionLock.sessionExerciseType

    /** Only call with the classifier's stable result during an active session. */
    fun observeDetectedExercise(type: ExerciseType): Boolean = sessionLock.observe(type).also { locked ->
        if (locked) _exerciseType.value = sessionExerciseType.name
    }
    private fun effectiveType(legacyType: String) = if (sessionExerciseType in SessionExerciseLock.supported)
        sessionExerciseType.name else legacyType.uppercase()
    private val records = mutableListOf<RepRecord>()
    private val structuredReps = mutableListOf<com.example.arptapp.domain.report.StructuredRepAnalysis>()
    private val _exerciseType = MutableStateFlow("IDLE")
    val exerciseType: StateFlow<String> = _exerciseType.asStateFlow()

    fun updateExerciseType(type: String) {
        if (sessionExerciseType in SessionExerciseLock.supported) {
            _exerciseType.value = sessionExerciseType.name
            return
        }
        _exerciseType.value = type.uppercase().let {
            if (it == "SQUAT" || it == "SHOULDER_PRESS") it else "IDLE"
        }
    }

    fun resetSession() {
        sessionLock.reset()
        records.clear()
        structuredReps.clear()
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

    /** Live path: structural capture -> local score -> both record/report representations, atomically. */
    fun addCompletedRep(record: RepRecord, structured: StructuredRepAnalysis?, cameraView: String,
                        referenceSimilarity: Float? = null): StructuredRepAnalysis? {
        if (sessionExerciseType !in SessionExerciseLock.supported || record.repNumber <= 0 ||
            records.any { it.repNumber == record.repNumber } || !record.maxAngle.isFinite() || !record.swayX.isFinite()) return null
        if (structured != null && (structured.exerciseType != sessionExerciseType.name || structured.repNumber != record.repNumber)) return null
        val measured = structured ?: StructuredRepAnalysis(record.repNumber, sessionExerciseType.name, cameraView,
            null, PhaseDurations(), JointMetrics(), AsymmetryMetrics(), emptyList(), emptyList(), emptyList(),
            "이 회차의 측정 근거가 부족합니다.", partialCapture = true, phaseModelVersion = 2)
        val scored = RepScoreCalculator.score(measured, referenceSimilarity)
        records += record.copy(poseScore = scored.referenceSimilarity)
        structuredReps += scored
        return scored
    }

    fun addStructuredRep(rep: com.example.arptapp.domain.report.StructuredRepAnalysis) {
        if (records.any { it.repNumber == rep.repNumber } && structuredReps.none { it.repNumber == rep.repNumber }) {
            structuredReps += rep
        }
    }

    fun structuredReport(exercise: String): com.example.arptapp.domain.report.StructuredWorkoutAnalysis {
        val type = effectiveType(exercise)
        val analyses = getRepAnalyses(type)
        return com.example.arptapp.domain.report.WorkoutAnalysisAggregator
            .aggregate(type, structuredReps.toList(), records.size).copy(
                averageScore = analyses.takeIf { it.isNotEmpty() }?.map { it.score.toDouble() }?.average()?.toFloat(),
                bestRepNumber = analyses.maxByOrNull { it.score }?.repNumber,
                weakestRepNumber = analyses.minByOrNull { it.score }?.repNumber
            )
    }

    fun getRepAnalyses(exerciseType: String): List<RepAnalysis> =
        reportAnalyzer.analyze(records, effectiveType(exerciseType), structuredReps)

    fun generateFinalReport(exerciseType: String): SessionReport =
        reportAnalyzer.report(records, effectiveType(exerciseType), structuredReps)

    /** Never create/store/upload a numeric zero placeholder for an unconfirmed or unmeasurable session. */
    fun generateLockedSessionReport(): SessionReport? {
        if (sessionExerciseType !in SessionExerciseLock.supported || records.isEmpty() ||
            getRepAnalyses(sessionExerciseType.name).isEmpty()) return null
        return reportAnalyzer.report(records, sessionExerciseType.name, structuredReps)
    }
}
