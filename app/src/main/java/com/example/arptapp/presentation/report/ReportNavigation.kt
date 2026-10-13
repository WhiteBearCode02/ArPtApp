package com.example.arptapp.presentation.report

import android.content.Context
import android.content.Intent
import com.example.arptapp.data.RepAnalysisCodec
import com.example.arptapp.ResultActivity
import com.example.arptapp.model.RepAnalysis

/**
 * Dashboard의 기존 문자열 extra 및 예전 SCORES 배열을 그대로 읽는 화면 간 API.
 * 키 이름/단위를 변경할 때는 읽기 호환을 먼저 추가한 뒤 모든 호출부를 변경해야 한다.
 */
object ReportNavigation {
    private const val EXERCISE_TYPE = "EXERCISE_TYPE"
    private const val TOTAL_COUNT = "TOTAL_COUNT"
    private const val AVG_SCORE = "AVG_SCORE"
    private const val SCORES = "SCORES"
    private const val REP_ANALYSES_JSON = "REP_ANALYSES_JSON"
    private const val WORKOUT_DATE = "WORKOUT_DATE"
    private const val EXERCISE_TIME = "EXERCISE_TIME"
    private const val BURNED_CALORIES = "BURNED_CALORIES"
    private const val FEEDBACK_MESSAGE = "FEEDBACK_MESSAGE"

    fun read(intent: Intent): WorkoutReportData = WorkoutReportData.create(
        exerciseType = intent.getStringExtra(EXERCISE_TYPE),
        totalCount = intent.getIntExtra(TOTAL_COUNT, 0),
        averageScore = intent.getFloatExtra(AVG_SCORE, 0f),
        analysesJson = intent.getStringExtra(REP_ANALYSES_JSON),
        legacyScores = intent.getFloatArrayExtra(SCORES)?.toList().orEmpty(),
        workoutDate = intent.getStringExtra(WORKOUT_DATE).orEmpty(),
        durationSeconds = intent.getLongExtra(EXERCISE_TIME, 0L),
        burnedCalories = if (intent.hasExtra(BURNED_CALORIES)) intent.getDoubleExtra(BURNED_CALORIES, 0.0) else null,
        feedbackMessage = intent.getStringExtra(FEEDBACK_MESSAGE).orEmpty()
    )

    fun intent(context: Context, report: WorkoutReportData): Intent =
        write(Intent(context, ReportActivity::class.java), report)

    fun resultIntent(context: Context, report: WorkoutReportData): Intent =
        write(Intent(context, ResultActivity::class.java), report)

    private fun write(intent: Intent, report: WorkoutReportData): Intent = intent.apply {
            putExtra(EXERCISE_TYPE, report.exerciseType)
            putExtra(TOTAL_COUNT, report.totalCount)
            putExtra(AVG_SCORE, report.averageScore)
            putExtra(REP_ANALYSES_JSON, report.analysesJson)
            putExtra(WORKOUT_DATE, report.workoutDate)
            putExtra(EXERCISE_TIME, report.durationSeconds)
            // 미확정 열량은 결과 화면에서 기존 추정식을 적용하므로 0으로 덮어쓰지 않는다.
            report.burnedCalories?.let { putExtra(BURNED_CALORIES, it) }
            putExtra(FEEDBACK_MESSAGE, report.feedbackMessage)
        }

    /** Codec는 구버전 숫자 배열과 현재 회차 객체 배열을 모두 지원하고 손상된 기록은 빈 목록으로 처리한다. */
    fun analyses(report: WorkoutReportData): List<RepAnalysis> = RepAnalysisCodec.decode(report.analysesJson)
}
