package com.example.arptapp.presentation.report

import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.data.YoloSessionDiagnosticsCodec
import com.example.arptapp.model.RepAnalysis
import java.util.Locale

/**
 * 결과 화면과 과거 기록이 공유하는 보고서 계약. 시간은 초, 열량은 kcal, 점수는 0..100이다.
 * JSON은 기존 Room 기록과의 호환을 위해 그대로 전달하고, 해석은 [ReportNavigation]에서 한다.
 */
@ConsistentCopyVisibility
data class WorkoutReportData private constructor(
    val exerciseType: String,
    val totalCount: Int,
    val averageScore: Float,
    val analysesJson: String,
    val workoutDate: String,
    val durationSeconds: Long,
    val burnedCalories: Double?,
    val feedbackMessage: String,
    /** Separate bounded local-only envelope; analysis/score/remote payloads must not consume it. */
    val yoloDiagnosticsJson: String?
) {
    /** 완료 일시는 한 번 확정한다. 칼로리의 0.5 kcal/회는 기존 공학적 추정값이며 실측값이 아니다. */
    fun completedAt(date: String): WorkoutReportData = copy(
        workoutDate = workoutDate.ifBlank { date },
        burnedCalories = burnedCalories ?: totalCount * 0.5,
        feedbackMessage = feedbackMessage.ifBlank { WorkoutReportPresentation.feedback(averageScore) }
    )

    /** 인증된 계정 소유자만 로컬 행에 연결한다. 비밀번호/토큰은 보고서에 포함하지 않는다. */
    fun toRecord(userId: String): ExerciseRecord {
        require(userId.isNotBlank()) { "운동 기록을 저장할 계정이 필요합니다." }
        return ExerciseRecord(
            userId = userId,
            date = workoutDate,
            totalCount = totalCount,
            duration = durationSeconds,
            burnedCalories = burnedCalories ?: 0.0,
            exerciseType = exerciseType,
            averageScore = averageScore,
            scoresJson = analysesJson,
            feedbackMessage = feedbackMessage,
            yoloDiagnosticsJson = yoloDiagnosticsJson
        )
    }

    companion object {
        /** 외부 전달값과 이전 버전 데이터를 UI에 넣기 전에 정규화하는 유일한 진입점. */
        fun create(
            exerciseType: String? = null,
            totalCount: Int = 0,
            averageScore: Float = 0f,
            analysesJson: String? = null,
            legacyScores: List<Float> = emptyList(),
            workoutDate: String = "",
            durationSeconds: Long = 0L,
            burnedCalories: Double? = null,
            feedbackMessage: String = "",
            yoloDiagnosticsJson: String? = null
        ): WorkoutReportData = WorkoutReportData(
            exerciseType = exerciseType?.takeIf { it.isNotBlank() } ?: "운동 기록",
            totalCount = totalCount.coerceAtLeast(0),
            averageScore = averageScore.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f,
            // NaN/Infinity는 JSON 숫자가 아니다. null로 남겨 이전 회차의 인덱스는 보존한다.
            analysesJson = analysesJson ?: legacyScores.joinToString(prefix = "[", postfix = "]") {
                if (it.isFinite() && it in 0f..100f) it.toString() else "null"
            },
            workoutDate = workoutDate,
            durationSeconds = durationSeconds.coerceAtLeast(0L),
            burnedCalories = burnedCalories?.takeIf { it.isFinite() && it >= 0.0 },
            feedbackMessage = feedbackMessage,
            // Strict decoder rejects malformed/oversized/unexpected data before local navigation.
            yoloDiagnosticsJson = YoloSessionDiagnosticsCodec.decode(yoloDiagnosticsJson)
                ?.let(YoloSessionDiagnosticsCodec::encode)
        )

        fun fromRecord(record: ExerciseRecord): WorkoutReportData = create(
            exerciseType = record.exerciseType,
            totalCount = record.totalCount,
            averageScore = record.averageScore,
            analysesJson = record.scoresJson,
            workoutDate = record.date,
            durationSeconds = record.duration,
            burnedCalories = record.burnedCalories,
            feedbackMessage = record.feedbackMessage,
            yoloDiagnosticsJson = record.yoloDiagnosticsJson
        )
    }
}

/** Android 화면과 무관한 표시 규칙. 분석 점수는 의학적 판정이나 올바른 자세의 보증이 아니다. */
object WorkoutReportPresentation {
    fun formatDuration(seconds: Long): String {
        val safeSeconds = seconds.coerceAtLeast(0L)
        return String.format(Locale.KOREA, "%02d분 %02d초", safeSeconds / 60, safeSeconds % 60)
    }

    fun displayScore(report: WorkoutReportData, analyses: List<RepAnalysis>): Float? {
        if (analyses.isEmpty()) return null
        return if (report.averageScore > 0f) report.averageScore
        else analyses.map { it.score }.average().toFloat()
    }

    fun feedback(score: Float): String = when {
        score >= 90f -> "현재 분석 기준과 유사한 동작이 기록되었습니다. 편안한 범위에서 일정하게 반복해 보세요."
        score >= 70f -> "관절 각도와 회차별 관찰 내용을 확인하고 동작 속도를 일정하게 유지해 보세요."
        score > 0f -> "촬영 방향과 관절 가시성을 확인하고 무리하지 않는 범위에서 천천히 반복해 보세요."
        else -> "자세 점수를 계산할 수 있는 운동 데이터가 부족합니다."
    }
}
